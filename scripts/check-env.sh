#!/usr/bin/env bash
# 工具链前置检查。只做检查与提示，不擅自安装任何东西。
set -uo pipefail

fail=0
note() { printf '%-14s %s\n' "$1" "$2"; }
warn() { note "$1" "缺失（$2）"; fail=1; }

# JDK 25：由 Maven Toolchains 与 CI 双重锁定，本检查只做快速核对
if command -v java >/dev/null 2>&1; then
    jv=$(java -version 2>&1 | head -1)
    case "$jv" in
        *\"25*) note "java" "OK  $(java -version 2>&1 | head -1)" ;;
        *)      warn "java" "需要 JDK 25，当前为：$jv" ;;
    esac
else
    warn "java" "未安装"
fi

if [ -x "$(dirname "$0")/../backend/mvnw" ]; then
    note "maven" "OK  Wrapper（3.9.9），无需预装"
else
    warn "maven" "缺少 backend/mvnw"
fi

if command -v docker >/dev/null 2>&1; then
    note "docker" "OK  $(docker --version)"
else
    warn "docker" "未安装；compose 与 Testcontainers 依赖它，可在虚拟机内执行"
fi

if command -v node >/dev/null 2>&1; then
    node_major=$(node -p "process.versions.node.split('.')[0]")
    if [ "$node_major" -ge 20 ]; then
        note "node" "OK  $(node --version)"
    else
        warn "node" "需要 ≥20，当前 $(node --version)"
    fi
else
    warn "node" "未安装"
fi

if command -v pnpm >/dev/null 2>&1; then
    note "pnpm" "OK  $(pnpm --version)"
else
    warn "pnpm" "未安装；S3 环境准备期确定安装方式"
fi

echo
if [ "$fail" -ne 0 ]; then
    echo "存在缺失项，请先补齐再执行 make setup。"
    exit 1
fi
echo "工具链检查通过。"
