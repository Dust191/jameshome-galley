#!/usr/bin/env bash
# 按 docs/05-governance/labels.md 创建仓库标签（幂等）。
#
# 用法：scripts/setup-labels.sh <owner>/<repo>
# 前置：已安装 GitHub CLI 并 `gh auth login`；对目标仓库有 admin 权限。
#
# 默认标签（bug / enhancement / question ...）会先被删除，否则总数会超过 25 的上限。
set -uo pipefail

REPO="${1:-}"
if [ -z "$REPO" ]; then
    echo "用法：scripts/setup-labels.sh <owner>/<repo>"
    exit 1
fi
command -v gh >/dev/null 2>&1 || { echo "需要 GitHub CLI（gh）"; exit 1; }

for name in bug documentation duplicate enhancement invalid question wontfix \
            "good first issue" "help wanted" \
            accessibility dependencies github_actions java javascript; do
    gh label delete "$name" --repo "$REPO" --yes >/dev/null 2>&1 || true
done

created=0
label() {
    if gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force >/dev/null; then
        created=$((created + 1))
    else
        echo "✗ 创建失败：$1" >&2
    fi
}

# 类型
label type/bug          d73a4a "与文档或预期行为不符的缺陷"
label type/feature      1d76db "新增能力或行为增强"
label type/docs         0075ca "文档缺失、过期或与实现不符"
label type/refactor     fbca04 "不改外部行为的结构调整"
label type/security     b60205 "安全缺陷或加固，走 SECURITY.md 私密渠道"
label type/question     d876e3 "用法或设计意图的疑问"

# 领域
label area/api          5319e7 "后端接口与契约"
label area/web          006b75 "前端页面与交互"
label area/db           0e8a16 "表结构与迁移"
label area/stock        c2e0c6 "流水、结存、对账"
label area/stats        c5def5 "统计与预警"
label area/infra        bfdadc "构建、部署、编排"
label area/docs         fef2c0 "文档体系"

# 优先级
label p0/blocker        b60205 "阻断使用或数据正确性受损，24 小时内响应"
label p1/high           d93f0b "核心路径受损但有绕过办法，3 个工作日内响应"
label p2/normal         fbca04 "常规缺陷与计划内改进，7 个工作日内响应"
label p3/low            e4e669 "体验瑕疵与边缘场景"

# 状态
label status/needs-triage ededed "尚未分类，补 type 与 priority 后移除"
label status/accepted   0e8a16 "已确认，等待排期"
label status/in-progress fbca04 "有人在做，PR 合并或关闭时移除"
label status/blocked    d93f0b "被外部条件阻塞，描述里写明阻塞点"
label status/wontfix    ffffff "决定不处理，须写明理由并允许复议一次"

# 引导
label "good first issue" 7057ff "适合首次贡献，必须附改哪里与怎么验证"
label "help wanted"      008672 "欢迎外部参与"
label breaking-change    e11d21 "破坏兼容，须在 CHANGELOG 标注"

total=$(gh label list --repo "$REPO" --limit 100 | wc -l | tr -d ' ')
echo "已创建/更新 ${created} 个标签，仓库现有 ${total} 个（上限 25）"
[ "$total" -le 25 ] || echo "⚠ 超过 25 个，请按 GOV-004 L-1 清理" >&2
