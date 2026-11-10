#!/usr/bin/env bash
# 提交信息格式校验（不依赖 Node，CI 与本地钩子共用同一规则表）。
# 规则：<type>(<scope>): <subject>；type/scope 白名单见下；subject ≤50 字且不以句号结尾。
# 豁免（与 check-signoff.sh 同口径）：合并提交、机器人作者（dependabot[bot] / github-actions[bot]）。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

types='feat|fix|refactor|perf|docs|test|build|ci|chore|revert'
scopes='api|web|db|auth|material|stock|stats|infra|docs|deps|release'
bots='^(dependabot\[bot\]|github-actions\[bot\])$'

range="${1:-HEAD}"
rc=0
count=0
skipped_merge=0
skipped_bot=0

while IFS=$'\t' read -r sha parents author subject; do
    [ -z "$subject" ] && continue
    # 豁免 1：合并提交（多父），不承载内容变更
    case "$parents" in
        *" "*) skipped_merge=$((skipped_merge + 1)); continue ;;
    esac
    # 豁免 2：机器人作者无法自守规则，署名责任由 Squash 合并的最终提交承担
    if echo "$author" | grep -qE "$bots"; then
        skipped_bot=$((skipped_bot + 1))
        continue
    fi
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
done < <(git log --format='%H%x09%P%x09%an%x09%s' "$range" 2>/dev/null)

if [ "$count" -eq 0 ] && [ "$skipped_merge" -eq 0 ] && [ "$skipped_bot" -eq 0 ]; then
    echo "范围内没有提交，跳过。"
    exit 0
fi

note=""
[ "$skipped_merge" -gt 0 ] && note="$note，豁免合并提交 $skipped_merge 条"
[ "$skipped_bot" -gt 0 ] && note="$note，豁免机器人提交 $skipped_bot 条"
if [ "$rc" -eq 0 ]; then
    echo "提交信息校验通过（检查 $count 条${note}）。"
fi
exit "$rc"
