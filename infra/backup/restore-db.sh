#!/usr/bin/env bash
#
# 恢复演练/实际恢复：把一个 pg_dump -Fc 备份恢复进**独立的目标库**，核对行数与迁移版本。
#
# 默认恢复到 galley_restore_check 并在核对通过后删除，用于周期性演练；
# 加 --keep 保留该库，便于人工进去看。恢复真实生产库请显式传 --target，且脚本拒绝覆盖生产库。
#
# 用法：
#   restore-db.sh <本地 dump 路径 | oss://bucket/key>
#   restore-db.sh oss://…/galley-20261112-213000.dump --target galley --confirm-production
#
set -euo pipefail

ENV_FILE="${GALLEY_ENV_FILE:-/opt/galley/.env.prod}"
COMPOSE_DIR="${GALLEY_COMPOSE_DIR:-/opt/galley}"
COMPOSE_FILE="${GALLEY_COMPOSE_FILE:-infra/compose/compose.prod.yml}"
DB_SERVICE="${GALLEY_DB_SERVICE:-db}"
PROD_DB="${GALLEY_DB_NAME:-galley}"
DRILL_DB="galley_restore_check"

usage() {
    cat <<'TXT'
用法：restore-db.sh <本地 dump 路径 | oss://bucket/key> [选项]

  --target <库名>          恢复目标库，默认 galley_restore_check（演练用）
  --keep                   核对后保留目标库，不自动删除
  --confirm-production     目标库等于生产库时必须显式给出，否则拒绝执行

示例：
  restore-db.sh /opt/galley/backup/db/galley-20261112-213000.dump
  restore-db.sh oss://jameshome-galley/backup/db/2026/11/galley-20261112-213000.dump
TXT
    exit 1
}

read_env() {
    [ -f "$ENV_FILE" ] || return 0
    sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r' |
        sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'\$//"
}

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_DIR/$COMPOSE_FILE" "$@"
}

log() { printf '%s %s\n' "$(date -Is)" "$*"; }
fail() {
    log "ERROR: $*"
    exit 1
}

[ $# -ge 1 ] || usage
SOURCE="$1"
shift

drill_start=$(date +%s)

TARGET="$DRILL_DB"
KEEP=0
CONFIRM_PRODUCTION=0
while [ $# -gt 0 ]; do
    case "$1" in
        --target)
            TARGET="$2"
            shift 2
            ;;
        --keep)
            KEEP=1
            shift
            ;;
        --confirm-production)
            CONFIRM_PRODUCTION=1
            shift
            ;;
        *) usage ;;
    esac
done

target_lower=$(printf '%s' "$TARGET" | tr '[:upper:]' '[:lower:]')
[ "$target_lower" != "postgres" ] && [ "$target_lower" != "template1" ] ||
    fail "拒绝以系统库 $TARGET 作为恢复目标"

if [ "$TARGET" = "$PROD_DB" ] && [ "$CONFIRM_PRODUCTION" -ne 1 ]; then
    fail "目标库 $TARGET 就是生产库；确认要覆盖请加 --confirm-production"
fi

db_user=$(read_env GALLEY_DB_USERNAME)
[ -n "$db_user" ] || fail "GALLEY_DB_USERNAME 未在 $ENV_FILE 中设置"

TMP_FILE=""
cleanup() {
    [ -n "$TMP_FILE" ] && rm -f "$TMP_FILE"
}
trap cleanup EXIT

# 1) 取备份文件：OSS 地址先下载到本地临时目录
if printf '%s' "$SOURCE" | grep -q '^oss://'; then
    command -v ossutil >/dev/null || fail "未安装 ossutil"
    bucket=$(read_env GALLEY_S3_BUCKET)
    endpoint=$(read_env GALLEY_S3_ENDPOINT)
    region=$(read_env GALLEY_S3_REGION)
    ak=$(read_env GALLEY_S3_ACCESS_KEY)
    sk=$(read_env GALLEY_S3_SECRET_KEY)
    [ -n "$endpoint" ] && [ -n "$ak" ] && [ -n "$sk" ] || fail "OSS 参数不全，无法下载"

    TMP_FILE=$(mktemp "/tmp/galley-restore-XXXXXX.dump")
    log "从 OSS 下载：$SOURCE"
    ossutil cp -f "$SOURCE" "$TMP_FILE" -e "$endpoint" --region "$region" -i "$ak" -k "$sk" >/dev/null ||
        fail "下载失败（地址或权限不对）"

    # 备份时同时上传了 .sha256；有就一起取下来做完整性比对
    ossutil cp -f "$SOURCE.sha256" "$TMP_FILE.sha256" \
        -e "$endpoint" --region "$region" -i "$ak" -k "$sk" >/dev/null 2>&1 || true
    FILE="$TMP_FILE"
else
    FILE="$SOURCE"
    [ -f "$FILE" ] || fail "备份文件不存在：$FILE"
    [ -f "$FILE.sha256" ] || log "提示：未找到 $FILE.sha256，跳过校验和比对"
fi

[ -s "$FILE" ] || fail "备份文件为空"
[ "$(head -c 5 "$FILE")" = "PGDMP" ] || fail "不是 pg_dump 自定义格式（-Fc）文件"

if [ -f "$FILE.sha256" ]; then
    expected=$(tr -d ' \n\r' <"$FILE.sha256")
    actual=$(sha256sum "$FILE" | awk '{print $1}')
    [ "$expected" = "$actual" ] || fail "sha256 不匹配：期望 ${expected:0:16}…，实际 ${actual:0:16}…"
    log "校验和一致：${actual:0:16}…"
fi

# 2) 建空目标库（FORCE 断开残留连接）
log "准备目标库：$TARGET"
compose exec -T "$DB_SERVICE" psql -U "$db_user" -d postgres -v ON_ERROR_STOP=1 -q \
    -c "DROP DATABASE IF EXISTS \"$TARGET\" WITH (FORCE)" \
    -c "CREATE DATABASE \"$TARGET\"" || fail "创建目标库失败"

# 3) 恢复并计时
log "开始恢复 → $TARGET"
start=$(date +%s)
if ! compose exec -T "$DB_SERVICE" pg_restore -U "$db_user" -d "$TARGET" \
    --no-owner --no-privileges --clean --if-exists <"$FILE"; then
    fail "pg_restore 返回非 0（部分对象可能已恢复，请查看上方的错误行）"
fi
restored=$(( $(date +%s) - start ))
log "恢复完成，用时 ${restored}s"

# 4) 核对：迁移版本 + 关键表行数
log "核对内容："
compose exec -T "$DB_SERVICE" psql -U "$db_user" -d "$TARGET" -v ON_ERROR_STOP=1 -tA <<'SQL' || fail "核对查询失败"
select 'flyway_max_version=' || coalesce(max(version), 'none')
  from flyway_schema_history where success;
select 'tables=' || count(*) from information_schema.tables
 where table_schema = 'public' and table_type = 'BASE TABLE';
select 'material=' || count(*) from material;
select 'stock_movement=' || count(*) from stock_movement;
select 'app_user=' || count(*) from app_user;
select 'material_category=' || count(*) from material_category;
select 'unit=' || count(*) from unit;
select 'audit_log=' || count(*) from audit_log;
SQL

# 5) 演练库默认删掉；保留时打印连接方式
if [ "$KEEP" -eq 1 ]; then
    log "已保留目标库 $TARGET（用完请手工 DROP DATABASE）"
else
    if [ "$TARGET" != "$PROD_DB" ]; then
        compose exec -T "$DB_SERVICE" psql -U "$db_user" -d postgres -q \
            -c "DROP DATABASE IF EXISTS \"$TARGET\" WITH (FORCE)" || log "警告：清理目标库失败"
        log "已清理目标库 $TARGET"
    fi
fi

log "演练完成，端到端耗时 $(( $(date +%s) - drill_start ))s（下载 + 建库 + 恢复 + 核对 + 清理）"
