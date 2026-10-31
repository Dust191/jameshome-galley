#!/usr/bin/env bash
# 提交信息格式校验（不依赖 Node，CI 与本地钩子共用同一规则表）。
# 规则：<type>(<scope>): <subject>；type/scope 白名单见下；subject ≤50 字且不以句号结尾。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

types='feat|fix|refactor|perf|docs|test|build|ci|chore|revert'
scopes='api|web|db|auth|material|stock|stats|infra|docs|deps|release'

range="${1:-HEAD}"
rc=0
count=0

while IFS= read -r subject; do
    [ -z "$subject" ] && continue
    count=$((count + 1))
    if ! echo "$subject" | grep -qE "^(${types})\(((${scopes}))\): .+"; then
        echo "✗ 不合规：$subject"
        echo "  期望形如 feat(stock): 出库数量超过结存时拦截"
        rc=1
        continue
    fi
    text=$(echo "$subject" | sed -E "s/^(${types})\(((${scopes}))\): //")
    len=$(echo -n "$text" | wc -m)
    if [ "$len" -gt 50 ]; then
        echo "✗ subject 超过 50 字（$len）：$subject"
        rc=1
    fi
    case "$text" in
        *。) echo "✗ subject 不得以句号结尾：$subject"; rc=1 ;;
    esac
done < <(git log --format='%s' "$range" 2>/dev/null)

if [ "$count" -eq 0 ]; then
    echo "范围内没有提交，跳过。"
    exit 0
fi

[ "$rc" -eq 0 ] && echo "提交信息校验通过（$count 条）。"
exit "$rc"
