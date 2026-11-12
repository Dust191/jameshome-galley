#!/usr/bin/env bash
# 校验环境变量文件与实现一致，共四组检查：
#   1) application.yml 读取的每个 ${GALLEY_XXX} 必须出现在 .env.example；
#   2) .env.example 定义的变量必须被代码读取（避免死变量），且均带 GALLEY_ 前缀；
#   3) compose.prod.yml 引用的变量必须出现在 infra/compose/.env.prod.example（生产模板不缺项、不多项）；
#   4) 生产模板自身格式：含空格的值必须加引号、文件不得含 CR（两个坑都真实踩过）。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

yml="backend/src/main/resources/application.yml"
envf=".env.example"
compose="infra/compose/compose.prod.yml"
prodenv="infra/compose/.env.prod.example"

for f in "$yml" "$envf" "$compose" "$prodenv"; do
    [ -f "$f" ] || {
        echo "缺少 $f"
        exit 1
    }
done

rc=0

# ---- 1、2 组：开发模板与代码 ----
used=$(grep -oE '\$\{GALLEY_[A-Z0-9_]+' "$yml" | sed 's/\${//' | sort -u)
defined=$(grep -oE '^[A-Z0-9_]+=' "$envf" | sed 's/=$//' | sort -u)
declared=$(grep -oE '^GALLEY_[A-Z0-9_]+=' "$envf" | sed 's/=$//' | sort -u)

missing=$(comm -23 <(echo "$used") <(echo "$declared"))
unused=$(comm -13 <(echo "$used") <(echo "$declared"))
nonprefixed=$(comm -23 <(echo "$defined") <(echo "$declared"))

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

# ---- 3 组：生产模板与编排 ----
# 先去掉 compose 里用 $$ 转义的容器内变量（如 $${POSTGRES_USER}），它们不由 env 文件提供
compose_used=$(sed 's/\$\${[A-Z0-9_]*}//g' "$compose" |
    grep -oE '\$\{[A-Z0-9_]+' | sed 's/\${//' | sort -u)
prod_declared=$(grep -oE '^[A-Z0-9_]+=' "$prodenv" | sed 's/=$//' | sort -u)

prod_missing=$(comm -23 <(echo "$compose_used") <(echo "$prod_declared"))
prod_unused=$(comm -13 <(echo "$compose_used") <(echo "$prod_declared"))

if [ -n "$prod_missing" ]; then
    echo "编排引用但 .env.prod.example 未提供："
    echo "$prod_missing" | sed 's/^/  - /'
    rc=1
fi
if [ -n "$prod_unused" ]; then
    echo ".env.prod.example 定义但编排未使用："
    echo "$prod_unused" | sed 's/^/  - /'
    rc=1
fi

# ---- 4 组：生产模板的格式 ----
if grep -q $'\r' "$prodenv"; then
    echo "生产模板含 CR（CRLF 行尾）：值尾会多一个 \\r，shell 与 ossutil 都会出错"
    rc=1
fi

unquoted=$(awk -F= '/^[A-Z0-9_]+=/ {
    key = $1
    val = substr($0, length(key) + 2)
    if (val ~ /[ \t]/ && val !~ /^["'"'"']/) print NR ": " key "=" val
}' "$prodenv")
if [ -n "$unquoted" ]; then
    echo "生产模板中以下值含空格却未加引号（source 时会被当命令执行）："
    echo "$unquoted" | sed 's/^/  - /'
    rc=1
fi

[ "$rc" -eq 0 ] && echo "环境变量一致性校验通过（开发 $(echo "$used" | wc -l | tr -d ' ') 项，生产 $(echo "$compose_used" | wc -l | tr -d ' ') 项）。"
exit "$rc"
