#!/usr/bin/env bash
# 按 tag 保留最近 N 个 api / web 镜像，其余从本机删除。
#
# 用法：
#   ./prune-images.sh              # 只看会删什么
#   ./prune-images.sh --apply      # 真删
#   KEEP=10 REPOS="api web" ./prune-images.sh --apply
#
# 说明：ACR 个人版没有保留策略，仓库侧仍保留全部 tag；本脚本管的是机器上的本地副本，
# 避免一台 40 GB 的机器被历史镜像占满。正在被容器使用的 tag 一律跳过。
set -euo pipefail

KEEP="${KEEP:-10}"
ACR_PREFIX="${ACR_PREFIX:-crpi-8wtwv1zju2g9kpgg-vpc.cn-hangzhou.personal.cr.aliyuncs.com/jameshome-galley}"
REPOS="${REPOS:-api web}"
APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

removed=0
kept=0

for repo in $REPOS; do
  ref="${ACR_PREFIX}/${repo}"
  # 取该仓库下的全部 tag，按版本号排序（v 前缀与纯数字都能排）
  mapfile -t tags < <(docker images --format '{{.Repository}}:{{.Tag}}' "$ref" 2>/dev/null \
    | sed "s|^${ref}:||" | grep -v '^<none>$' | sort -V)
  total=${#tags[@]}
  if [ "$total" -le "$KEEP" ]; then
    echo "[skip] ${repo}: ${total} 个 tag，未超过 ${KEEP}"
    kept=$((kept + total))
    continue
  fi
  drop=$((total - KEEP))
  echo "[plan] ${repo}: 共 ${total} 个，保留最近 ${KEEP} 个，删除 ${drop} 个"
  for ((i = 0; i < drop; i++)); do
    img="${ref}:${tags[$i]}"
    if docker ps -a --format '{{.Image}}' | grep -qxF "$img"; then
      echo "  [in-use] $img"
      continue
    fi
    if [ "$APPLY" = 1 ]; then
      docker image rm "$img" >/dev/null 2>&1 && echo "  deleted $img" && removed=$((removed + 1)) \
        || echo "  [fail] $img"
    else
      echo "  would delete $img"
    fi
  done
  kept=$((kept + KEEP))
done

echo "完成：保留 ${kept} 个，删除 ${removed} 个（APPLY=${APPLY}）"
