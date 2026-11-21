#!/usr/bin/env bash
# 回滚：把 api / web 切回指定 tag，重建并验证；失败时自动退回原配置。
#
# 用法：
#   ./rollback.sh --to 0.2.0              # 回滚到 0.2.0
#   ./rollback.sh --to 0.2.2 --dry-run    # 只打印将要做什么
#
# 退出码：0 成功；1 参数或前置检查失败；2 重建后验证失败（已自动退回原 tag）。
set -euo pipefail

ENV_FILE="${GALLEY_ENV_FILE:-/opt/galley/.env.prod}"
COMPOSE_DIR="${GALLEY_COMPOSE_DIR:-/opt/galley/infra/compose}"
COMPOSE_FILE="${COMPOSE_FILE:-compose.prod.yml}"
ACR_HOST="${ACR_HOST:-crpi-8wtwv1zju2g9kpgg-vpc.cn-hangzhou.personal.cr.aliyuncs.com/jameshome-galley}"

TARGET=""
DRY_RUN=0
COMPONENTS="api web"

usage() { sed -n '2,9p' "$0"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --to) TARGET="${2:-}"; shift 2 ;;
    --env-file) ENV_FILE="${2:-}"; shift 2 ;;
    --only) COMPONENTS="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage; exit 1 ;;
  esac
done

[ -n "$TARGET" ] || { echo "必须给 --to <tag>" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "环境文件不存在：$ENV_FILE" >&2; exit 1; }
cd "$COMPOSE_DIR"

get_val() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r' | sed 's/^"//; s/"$//'; }
set_val() { # $1 key, $2 value
  if grep -qE "^$1=" "$ENV_FILE"; then
    sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE"
  fi
}

CUR_API="$(get_val GALLEY_IMAGE_API)"
CUR_WEB="$(get_val GALLEY_IMAGE_WEB)"
OLD_API_TAG="${CUR_API##*:}"
OLD_WEB_TAG="${CUR_WEB##*:}"

echo "[1/6] 当前：api=$OLD_API_TAG web=$OLD_WEB_TAG → 目标 $TARGET"

for comp in $COMPONENTS; do
  case "$comp" in
    api) IMG="$ACR_HOST/api:$TARGET" ;;
    web) IMG="$ACR_HOST/web:$TARGET" ;;
    *) echo "未知组件：$comp" >&2; exit 1 ;;
  esac
  if ! docker image inspect "$IMG" >/dev/null 2>&1; then
    echo "[2/6] 本地没有 $IMG，拉取"
    [ "$DRY_RUN" = 1 ] && { echo "  (dry-run) docker pull $IMG"; continue; }
    docker pull "$IMG" >/dev/null
  fi
done

if [ "$DRY_RUN" = 1 ]; then
  echo "[dry-run] 将把 $ENV_FILE 改为："
  for comp in $COMPONENTS; do
    case "$comp" in
      api) echo "  GALLEY_IMAGE_API=$ACR_HOST/api:$TARGET" ;;
      web) echo "  GALLEY_IMAGE_WEB=$ACR_HOST/web:$TARGET" ;;
    esac
  done
  echo "[dry-run] 然后重建：docker compose up -d --no-deps --force-recreate $COMPONENTS"
  exit 0
fi

BACKUP_ENV="/tmp/galley-env-rollback-$(date +%Y%m%d-%H%M%S)"
cp "$ENV_FILE" "$BACKUP_ENV"
echo "[3/6] 已备份环境文件到 $BACKUP_ENV"

for comp in $COMPONENTS; do
  case "$comp" in
    api) set_val GALLEY_IMAGE_API "$ACR_HOST/api:$TARGET" ;;
    web) set_val GALLEY_IMAGE_WEB "$ACR_HOST/web:$TARGET" ;;
  esac
done

START=$(date +%s)

rollback_to_old() {
  echo "!! 回滚失败，退回原 tag（api=$OLD_API_TAG web=$OLD_WEB_TAG）"
  cp "$BACKUP_ENV" "$ENV_FILE"
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --force-recreate $COMPONENTS >/dev/null || true
  exit 2
}

echo "[4/6] 重建 $COMPONENTS"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --force-recreate $COMPONENTS || rollback_to_old

echo "[5/6] 等待健康"
for i in $(seq 1 60); do
  ok=1
  for comp in $COMPONENTS; do
    cid="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q "$comp" || true)"
    [ -n "$cid" ] || { ok=0; break; }
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")"
    [ "$status" = "healthy" ] || ok=0
  done
  [ "$ok" = 1 ] && break
  [ "$i" = 60 ] && { echo "健康检查超时"; rollback_to_old; }
  sleep 2
done

echo "[6/6] 冒烟"
docker exec galley-api-1 curl -fsS -m 5 http://127.0.0.1:8080/actuator/health/readiness >/dev/null || rollback_to_old
docker exec galley-web-1 wget -q -O /dev/null http://127.0.0.1/ || rollback_to_old

END=$(date +%s)
ELAPSED=$((END - START))
echo "回滚完成：$OLD_API_TAG/$OLD_WEB_TAG → $TARGET，耗时 ${ELAPSED} 秒"
echo "$(date '+%F %T') rollback api/web → $TARGET 耗时 ${ELAPSED}s" >> /opt/galley/backup/rollback.log
[ "$ELAPSED" -le 300 ] || { echo "超过 5 分钟门槛"; exit 3; }
