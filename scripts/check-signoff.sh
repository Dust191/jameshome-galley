#!/usr/bin/env bash
# 提交署名校验：Signed-off-by 必须存在且与提交者一致；
# Assisted-by 若存在，不得为占位词，且必须命中 CONTRIBUTORS.md 登记表。
#
# 两类豁免（GOV-000 §6.4 / CONTRIBUTING §8）：
#   1. 合并提交（merge commit）不承载内容变更，DCO 只约束内容提交；
#   2. 机器人作者（dependabot[bot] / github-actions[bot]）无法签署，
#      署名责任由 Squash 合并产生的最终提交承担。
# 用法：scripts/check-signoff.sh [git 范围]，默认为最近 20 个提交
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

range="${1:-}"
if [ -z "$range" ]; then
    if git rev-parse HEAD~20 >/dev/null 2>&1; then range="HEAD~20..HEAD"; else range="HEAD"; fi
fi

contrib="CONTRIBUTORS.md"
[ -f "$contrib" ] || { echo "缺少 $contrib"; exit 1; }

blacklist='^(unknown|n/?a|ai|assistant|placeholder)$'
bots='^(dependabot\[bot\]|github-actions\[bot\])$'
rc=0
total=0
skipped=0

for sha in $(git rev-list "$range" 2>/dev/null); do
    total=$((total + 1))
    short=$(echo "$sha" | cut -c1-7)
    author=$(git show -s --format='%an' "$sha")
    email=$(git show -s --format='%ae' "$sha")
    subject=$(git show -s --format='%s' "$sha")
    body=$(git show -s --format='%b' "$sha")

    # 豁免 1：合并提交
    pcount=$(git rev-list --parents -n 1 "$sha" | wc -w | tr -d ' ')
    if [ "$pcount" -gt 2 ]; then
        skipped=$((skipped + 1))
        continue
    fi

    # 豁免 2：机器人提交
    if printf '%s' "$author" | grep -qE "$bots"; then
        skipped=$((skipped + 1))
        continue
    fi

    signed=$(printf '%s\n' "$body" | grep -iE '^Signed-off-by: ' | head -1 || true)
    if [ -z "$signed" ]; then
        echo "[FAIL] $short 缺少 Signed-off-by: $subject"
        rc=1
    else
        name=$(printf '%s' "$signed" | sed -E 's/^[Ss]igned-off-by: (.*) <.*>/\1/')
        mail=$(printf '%s' "$signed" | sed -E 's/^[Ss]igned-off-by: .* <(.*)>/\1/')
        if [ "$name" != "$author" ] || [ "$mail" != "$email" ]; then
            echo "[FAIL] $short Signed-off-by 与作者不一致: $name <$mail> vs $author <$email>"
            rc=1
        fi
    fi

    assisted=$(printf '%s\n' "$body" | grep -iE '^Assisted-by: ' | head -1 | sed -E 's/^[Aa]ssisted-by: //' || true)
    if [ -n "$assisted" ]; then
        if printf '%s' "$assisted" | grep -qiE "$blacklist"; then
            echo "[FAIL] $short Assisted-by 使用了占位词: $assisted"
            rc=1
        elif ! grep -qF "$assisted" "$contrib"; then
            echo "[FAIL] $short Assisted-by 未在 $contrib 登记: $assisted"
            rc=1
        fi
    fi
done

if [ "$total" -eq 0 ]; then
    echo "范围内没有提交，跳过。"
    exit 0
fi

[ "$rc" -eq 0 ] && echo "署名校验通过（$total 条提交，其中豁免 $skipped 条：合并提交与机器人）。"
exit "$rc"
