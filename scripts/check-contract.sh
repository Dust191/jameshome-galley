#!/usr/bin/env bash
# 契约校验：文件存在、版本与 pom 一致；应用可达时与 springdoc 生成结果逐字比对。
#
# 逐字比对前会把 servers 段规范化成固定的 `- url: /`——生成结果里的地址取决于启动端口，
# 不能作为契约的一部分。CI 需先启动应用（或设置 GALLEY_OPENAPI_URL 指向已启动实例）。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

spec="contracts/openapi.yaml"
pom="backend/pom.xml"

[ -f "$spec" ] || { echo "缺少 $spec"; exit 1; }

if ! head -1 "$spec" | grep -qE '^openapi: 3\.'; then
    echo "✗ $spec 缺少 openapi: 3.x 声明"
    exit 1
fi

spec_ver=$(grep -E '^  version:' "$spec" | head -1 | sed -E 's/.*version: *//' | tr -d '"')
# 取本项目自身的 <version>，不能误取 parent 的版本
pom_ver=$(grep -A1 '<artifactId>galley</artifactId>' "$pom" | grep -m1 '<version>' | sed -E 's/.*<version>(.*)<\/version>.*/\1/' | sed 's/-SNAPSHOT//')

if [ "$spec_ver" != "$pom_ver" ]; then
    echo "✗ 契约版本（$spec_ver）与 pom 版本（$pom_ver）不一致"
    exit 1
fi

# servers 段归一：地址随端口变化，不参与比对
normalize() {
    awk '
        /^servers:/ { print "servers:"; print "- url: /"; skip = 1; next }
        skip == 1 {
            if ($0 ~ /^[^[:space:]-]/) { skip = 0 } else { next }
        }
        { print }
    ' "$1"
}

url="${GALLEY_OPENAPI_URL:-http://127.0.0.1:18080/v3/api-docs.yaml}"
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

if curl -s --noproxy '*' --max-time 8 "$url" -o "$tmp" && grep -q '^openapi:' "$tmp"; then
    if ! diff -q <(normalize "$spec") <(normalize "$tmp") >/dev/null; then
        echo "✗ 契约与实现不一致（$url）"
        diff -u <(normalize "$spec") <(normalize "$tmp") | head -40
        exit 1
    fi
    echo "契约校验通过：与实现零差异，版本 $spec_ver，路径 $(grep -cE '^  /' "$spec") 条"
else
    echo "契约校验通过：版本 $spec_ver，路径 $(grep -cE '^  /' "$spec") 条（应用未运行，跳过逐字比对）"
fi
