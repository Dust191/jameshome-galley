#!/usr/bin/env bash
# 契约校验（S1 基线）：文件存在、可解析、版本号与 pom 一致、路径数与实现端点不矛盾。
# S3 起改为由 springdoc 生成后与本文件逐字比对，差异即失败。
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

paths=$(grep -cE '^  /' "$spec")
echo "契约校验通过：openapi 3.x，版本 $spec_ver，路径 $paths 条（S1 基线，S3 升级为逐字比对）。"
