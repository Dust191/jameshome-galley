#!/usr/bin/env bash
# 提交署名校验：Signed-off-by 必须存在且与提交者一致；Assisted-by 若存在，必须命中 CONTRIBUTORS.md 登记表。
# 用法：scripts/check-signoff.sh [git 范围，默认 HEAD~20..HEAD 或全部]
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

range="${1:-}"
if [ -z "$range" ]; then
    if git rev-parse HEAD~20 >/dev/null 2>&1; then range="HEAD~20..HEAD"; else range="HEAD"; fi
fi

contrib="CONTRIBUTORS.md"
[ -f "$contrib" ] || { echo "缺少 $contrib"; exit 1; }

blacklist='^(unknown|n/?a|ai|assistant|placeholder)$'
rc=0
total=0

while IFS='|' read -r sha author email subject body; do
    [ -z "$sha" ] && continue
    total=$((total + 1))
    short=$(echo "$sha" | cut -c1-7)

    signed=$(echo "$body" | grep -iE '^Signed-off-by: ' | head -1)
    if [ -z "$signed" ]; then
        echo "✗ $short 缺少 Signed-off-by：$subject"
        rc=1
    else
        name=$(echo "$signed" | sed -E 's/^Signed-off-by: (.*) <.*>/\1/')
        mail=$(echo "$signed" | sed -E 's/^Signed-off-by: .* <(.*)>/\1/')
        if [ "$name" != "$author" ] || [ "$mail" != "$email" ]; then
            echo "✗ $short Signed-off-by 与作者不一致：$name <$mail> vs $author <$email>"
            rc=1
        fi
    fi

    assisted=$(echo "$body" | grep -iE '^Assisted-by: ' | head -1 | sed -E 's/^Assisted-by: //')
    if [ -n "$assisted" ]; then
        if echo "$assisted" | grep -qiE "$blacklist"; then
            echo "✗ $short Assisted-by 使用了占位词：$assisted"
            rc=1
        elif ! grep -qF "$assisted" "$contrib"; then
            echo "✗ $short Assisted-by 未在 $contrib 登记：$assisted"
            rc=1
        fi
    fi
done < <(git log --format='%H|%an|%ae|%s|%b' "$range" 2>/dev/null)

if [ "$total" -eq 0 ]; then
    echo "范围内没有提交，跳过。"
    exit 0
fi

[ "$rc" -eq 0 ] && echo "署名校验通过（$total 条提交）。"
exit "$rc"
