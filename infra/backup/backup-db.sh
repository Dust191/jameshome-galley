#!/usr/bin/env bash
#
# 生产数据库备份：pg_dump -Fc → 本地按日/周/月留存 → 上传内网 OSS → 校验上传结果。
#
# 由 cron 每日执行，也可手工跑。任一步失败立即以非 0 退出，便于告警捕获。
# 依赖：docker compose（编排在 COMPOSE_DIR）、ossutil（v2，/usr/local/bin）。
#
# 可用环境变量覆盖（默认值对应 /opt/galley 的部署布局）：
#   GALLEY_ENV_FILE      环境变量文件，默认 /opt/galley/.env.prod
#   GALLEY_COMPOSE_DIR   编排所在目录，默认 /opt/galley
#   GALLEY_COMPOSE_FILE  相对 COMPOSE_DIR 的编排文件名
#   GALLEY_BACKUP_ROOT   本地留存根目录，默认 /opt/galley/backup
#   GALLEY_DB_SERVICE    编排里的数据库服务名，默认 db
#   GALLEY_DB_NAME       库名，默认 galley
#   GALLEY_BACKUP_KEEP_DAILY / _WEEKLY / _MONTHLY  留存份数，默认 7 / 4 / 12
set -euo pipefail

ENV_FILE="${GALLEY_ENV_FILE:-/opt/galley/.env.prod}"
COMPOSE_DIR="${GALLEY_COMPOSE_DIR:-/opt/galley}"
COMPOSE_FILE="${GALLEY_COMPOSE_FILE:-infra/compose/compose.prod.yml}"
LOCAL_ROOT="${GALLEY_BACKUP_ROOT:-/opt/galley/backup}"
DB_SERVICE="${GALLEY_DB_SERVICE:-db}"
DB_NAME="${GALLEY_DB_NAME:-galley}"

KEEP_DAILY="${GALLEY_BACKUP_KEEP_DAILY:-7}"
KEEP_WEEKLY="${GALLEY_BACKUP_KEEP_WEEKLY:-4}"
KEEP_MONTHLY="${GALLEY_BACKUP_KEEP_MONTHLY:-12}"

DB_DIR="$LOCAL_ROOT/db"
LOG="$LOCAL_ROOT/backup.log"

umask 077
mkdir -p "$DB_DIR"

log() { printf '%s %s\n' "$(date -Is)" "$*" | tee -a "$LOG"; }
fail() {
    log "ERROR: $*"
    exit 1
}

# 只取需要的键：环境变量文件可能含带空格的值（如 cron 表达式），不整体 source
read_env() {
    [ -f "$ENV_FILE" ] || return 0
    sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r' |
        sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'\$//"
}

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_DIR/$COMPOSE_FILE" "$@"
}

# 本地留存：按日、周、月的最近 N 份取并集，其余删除（连同校验文件）
prune_local() {
    local -a files keep
    local f d wk mo n
    local -A kept=() seen_week=() seen_month=()
    local -a weeks=() months=()

    mapfile -t files < <(find "$DB_DIR" -maxdepth 1 -name 'galley-*.dump' -printf '%f\n' | sort -r)
    [ "${#files[@]}" -gt 0 ] || return 0

    n=0
    for f in "${files[@]}"; do
        if [ "$n" -lt "$KEEP_DAILY" ]; then
            kept["$f"]=1
        fi
        n=$((n + 1))
    done

    # 周与月各取「该周期内最新的一份」，再保留最近 N 个周期
    for f in "${files[@]}"; do
        d="${f#galley-}"
        d="${d%%-*}"
        wk=$(date -d "$d" +%G-%V 2>/dev/null || echo "unknown")
        mo="${d:0:6}"
        if [ -z "${seen_week[$wk]:-}" ]; then
            seen_week["$wk"]=1
            weeks+=("$f")
        fi
        if [ -z "${seen_month[$mo]:-}" ]; then
            seen_month["$mo"]=1
            months+=("$f")
        fi
    done

    local i
    for i in "${!weeks[@]}"; do
        [ "$i" -lt "$KEEP_WEEKLY" ] && kept["${weeks[$i]}"]=1
    done
    for i in "${!months[@]}"; do
        [ "$i" -lt "$KEEP_MONTHLY" ] && kept["${months[$i]}"]=1
    done

    local removed=0
    for f in "${files[@]}"; do
        if [ -z "${kept[$f]:-}" ]; then
            rm -f "$DB_DIR/$f" "$DB_DIR/$f.sha256"
            removed=$((removed + 1))
        fi
    done
    log "本地留存：保留 ${#kept[@]} 份（日 $KEEP_DAILY / 周 $KEEP_WEEKLY / 月 $KEEP_MONTHLY），清理 $removed 份"
}

main() {
    local db_user ts file start elapsed size prefix dest
    db_user=$(read_env GALLEY_DB_USERNAME)
    [ -n "$db_user" ] || fail "GALLEY_DB_USERNAME 未在 $ENV_FILE 中设置"
    ts=$(date +%Y%m%d-%H%M%S)
    file="$DB_DIR/galley-$ts.dump"

    log "开始备份：库 $DB_NAME → $(basename "$file")"
    start=$(date +%s)

    # 管道右侧失败不会让 set -e 生效，显式判空文件
    if ! compose exec -T "$DB_SERVICE" pg_dump -U "$db_user" -Fc -d "$DB_NAME" >"$file"; then
        rm -f "$file"
        fail "pg_dump 失败"
    fi
    [ -s "$file" ] || fail "dump 为空（服务 $DB_SERVICE 是否在运行？）"
    [ "$(head -c 5 "$file")" = "PGDMP" ] || fail "dump 头不是 PGDMP，文件不完整"

    size=$(stat -c %s "$file")
    sha256sum "$file" | awk '{print $1}' >"$file.sha256"
    elapsed=$(( $(date +%s) - start ))
    log "备份完成：$((size / 1024)) KB，耗时 ${elapsed}s，sha256 $(cut -c1-16 "$file.sha256")…"

    # 上传内网 endpoint：不经过公网、不计流量；桶保持私有读
    local bucket endpoint region ak sk
    bucket="${GALLEY_BACKUP_BUCKET:-$(read_env GALLEY_S3_BUCKET)}"
    endpoint=$(read_env GALLEY_S3_ENDPOINT)
    region=$(read_env GALLEY_S3_REGION)
    ak=$(read_env GALLEY_S3_ACCESS_KEY)
    sk=$(read_env GALLEY_S3_SECRET_KEY)
    [ -n "$bucket" ] && [ -n "$endpoint" ] && [ -n "$ak" ] && [ -n "$sk" ] ||
        fail "OSS 参数不全（bucket/endpoint/access-key/secret-key）"

    command -v ossutil >/dev/null || fail "未安装 ossutil（/usr/local/bin/ossutil）"

    prefix="backup/db/$(date +%Y/%m)"
    dest="oss://$bucket/$prefix/"
    local -a oss_args=(-e "$endpoint" --region "$region" -i "$ak" -k "$sk")

    ossutil cp -f "$file" "$dest" "${oss_args[@]}" >/dev/null ||
        fail "上传 dump 到 $dest 失败"
    ossutil cp -f "$file.sha256" "$dest" "${oss_args[@]}" >/dev/null ||
        fail "上传校验文件失败"

    # 上传后回查远端：大小必须一致；单段上传的 ETag 就是内容 MD5，能对上就更硬
    local stat_out remote_size remote_etag local_md5
    stat_out=$(ossutil stat "oss://$bucket/$prefix/$(basename "$file")" "${oss_args[@]}" 2>/dev/null || true)
    remote_size=$(printf '%s\n' "$stat_out" | sed -n 's/^Content-Length *: *\([0-9]*\).*/\1/p' | head -n 1)
    remote_etag=$(printf '%s\n' "$stat_out" | sed -n 's/^Etag *: *"\([0-9A-Fa-f]\{32\}\)".*/\1/p' | head -n 1)

    [ -n "$remote_size" ] || fail "取不到远端对象大小，视为上传未成功"
    [ "$remote_size" = "$size" ] || fail "远端大小 $remote_size 与本地 $size 不一致"

    local_md5=$(md5sum "$file" | awk '{print $1}')
    if [ -n "$remote_etag" ]; then
        [ "${remote_etag,,}" = "$local_md5" ] ||
            fail "远端 ETag $remote_etag 与本地 MD5 $local_md5 不一致"
        log "已上传并校验：$dest$(basename "$file")（$size B，MD5 一致）"
    else
        log "已上传并校验大小：$dest$(basename "$file")（$size B，ETag 非 MD5 形态，仅比对大小）"
    fi

    prune_local
    log "备份成功"
}

# 无参数＝完整备份；--prune-only 只按留存策略清理本地旧备份
case "${1:-}" in
    --prune-only)
        prune_local
        ;;
    --help | -h)
        sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
        ;;
    "")
        main
        ;;
    *)
        printf '未知参数：%s（可用：--prune-only、--help）\n' "$1" >&2
        exit 1
        ;;
esac
