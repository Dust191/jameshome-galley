#!/usr/bin/env bash
# 校验 .env.example 与代码实际读取的 GALLEY_ 变量是否一致。
# 规则：application.yml 中出现的每个 ${GALLEY_XXX} 必须在 .env.example 中定义；
#      .env.example 中定义的每个变量也必须被代码读取（避免死变量）。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

yml="backend/src/main/resources/application.yml"
envf=".env.example"

[ -f "$yml" ] || { echo "缺少 $yml"; exit 1; }
[ -f "$envf" ] || { echo "缺少 $envf"; exit 1; }

used=$(grep -oE '\$\{GALLEY_[A-Z0-9_]+' "$yml" | sed 's/\${//' | sort -u)
defined=$(grep -oE '^[A-Z0-9_]+=' "$envf" | sed 's/=$//' | sort -u)
declared=$(grep -oE '^GALLEY_[A-Z0-9_]+=' "$envf" | sed 's/=$//' | sort -u)

missing=$(comm -23 <(echo "$used") <(echo "$declared"))
unused=$(comm -13 <(echo "$used") <(echo "$declared"))
nonprefixed=$(comm -23 <(echo "$defined") <(echo "$declared"))

rc=0
if [ -n "$missing" ]; then
    echo "代码读取但 .env.example 未定义："
    echo "$missing" | sed 's/^/  - /'
    rc=1
fi
if [ -n "$unused" ]; then
    echo ".env.example 定义但代码未读取："
    echo "$unused" | sed 's/^/  - /'
    rc=1
fi
if [ -n "$nonprefixed" ]; then
    echo "变量名缺少 GALLEY_ 前缀："
    echo "$nonprefixed" | sed 's/^/  - /'
    rc=1
fi

[ "$rc" -eq 0 ] && echo "环境变量一致性校验通过（$(echo "$used" | wc -l | tr -d ' ') 项）。"
exit "$rc"
