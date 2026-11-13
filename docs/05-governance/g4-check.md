---
title: 阶段门 G4 验收记录
doc_id: GOV-006
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-03
updated: 2026-11-13
next_review: 2027-02-11
iter: ITER-20261113-03
related: [GOV-000, GOV-003, GOV-004, GOV-005, DEV-004, DEV-005]
---

# GOV-006 阶段门 G4 验收记录

这份文档回答一个问题：**S4 开源治理的每一条交付物与验收项是否达成、证据在哪、留下了哪些留痕的豁免。**

## 1. 结论

| 项 | 内容 |
|---|---|
| 阶段门 | G4（S4 开源治理） |
| 结论 | 12 条全部达成，其中 2 条为「配置已交付、单人期豁免」的留痕偏离 |
| 日期 | 2026-11-03 |
| 仓库 | github.com/Dust191/jameshome-galley（public，S3 冻结后建仓） |
| 依据 | 开发者 2026-11-03 指令：直接 G4 收尾，最后文档一并审定 |
| 开发者审定 | 通过（2026-11-03 22:00，与 ITER-20261103-04/05 一并审定）；S4 关账，S5 暂不开启，0.5.0 发布时机由开发者另行决定 |

## 2. 逐项核对（`GOV-000` §6.6）

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | LICENSE 为 Apache-2.0 官方全文 + 版权行 | 通过 | `LICENSE` 末行 `Copyright 2026 Dust191 (Original Author) & The JamesHome Contributors` |
| 2 | NOTICE 正文一字不差 | 通过 | `NOTICE` 首行 + §6.4 文本，与规范逐字一致 |
| 3 | README 含许可证声明、三条硬边界、末尾团队署名 | 通过 | README「三条硬边界」「许可证」两节 + 末行 `Maintained by The JamesHome Contributors` |
| 4 | CONTRIBUTING / CODE_OF_CONDUCT / SECURITY / GOVERNANCE 齐备且可执行 | 通过 | CONTRIBUTING 本轮重写：环境搭建、提交流程、评审规则、DCO；COC 有执行联系人与 CC BY 4.0；SECURITY 有时限与渠道 |
| 5 | CONTRIBUTORS.md 含署名段与两张表、模型登记有来源 | 通过 | 登记来源为「运行时声明（WorkBuddy）」，参与轮次更新至本轮 |
| 6 | Issue 三类模板可用、必填字段齐全 | 通过 | `.github/ISSUE_TEMPLATE/`：bug 表单 8 字段（环境/版本/复现/期望实际/日志/影响全必填）、feature 5 字段含范围自查、question 简版；blank issue 关闭 |
| 7 | PR 模板含文档影响 / 验证方式 / ITER 编号 / 模型协助声明 | 通过 | `.github/PULL_REQUEST_TEMPLATE.md` 十节；实际使用见 PR #23/#25/#29/#34 |
| 8 | 标签体系 ≤25 且每标签有定义 | 通过 | `GOV-004` 25 个标签 + `scripts/setup-labels.sh`；实测 25，Dependabot 重建后脚本复位 |
| 9 | CODEOWNERS 已配置，分支保护含 CODEOWNERS 审查 | 通过（豁免） | CODEOWNERS 按域书写；分支保护 `require_code_owner_reviews: false`——单人期无第二审批人，见 §3 |
| 10 | Dependabot 已启用，首次扫描无 High/Critical | 通过 | alerts：open 3 条全 medium（minimatch、react-router ×2）、fixed 2；本地 osv 终扫 0 High / 0 Critical、Moderate 4 |
| 11 | 抽查最近 10 条提交 trailer 三方一致 | 通过（含更正） | 4 条人类提交签署与作者一致、Assisted-by 在册；6 条机器人提交为豁免范围；发现并更正 1 处署名误标，见 §3 |
| 12 | 至少 1 个 issue 完成 triage → 修复 → 关闭（自测模拟） | 通过 | issue #14（DCO 误伤）→ PR #15 → CI 绿 → Squash 合并 → 自动关闭（CLOSED/COMPLETED） |

## 3. 留痕的偏离与更正

| 编号 | 事项 | 处置 | 收回条件 |
|---|---|---|---|
| V-1 | 分支保护未要求 CODEOWNERS 审查、approvals = 0 | 单人期无第二审批人（GitHub 禁止自批）；规则对贡献者生效，Owner 保留豁免（`enforce_admins: false`，与 GOV-003 §3 同源偏离） | S6 首名 Maintainer 入组即收紧 |
| V-2 | 6 条 Dependabot Squash 提交携带 `Assisted-by: DeepSeek-V4.1-Flash` | 抽查发现：该标注指「合并操作由模型代执行」，非变更内容协助，属误标。按约束 7 在此追加说明；main 禁止 force push，不回改历史；**后续机器人合并一律不带 Assisted-by** | 历史提交不回改；本条即更正记录 |
| V-3 | OWASP dependency-check 全量未执行 | 沿用 GOV-003 的边界：osv-scanner 在线查询为准，`DEV-003` §7 已说明 | CI 或受控环境（TD-09） |

## 4. 建仓后的增量能力（G3 未覆盖）

| 能力 | 状态 |
|---|---|
| 云端 CI（GitHub Runner）首次真实运行 | 全绿（后端 1m09s / 前端 30s / guards 7s），不再依赖 act 模拟（TD-05 部分偿还） |
| Squash-only 的 PR 流程实战 | #14/#15、#23、#25、#29、#34、#35 与 10 个依赖 PR 共 17 次合入，全部带 trailer |
| Dependabot 三生态周更 | ignore 21 条（maven 2 / npm 15 / actions 4），major 全部挡下；批次处置模式见 `ITER-20261103-05` §八~§十二 |
| 依赖卫生 | 12 个小版本已合入；Moderate 5 → 4；High/Critical 恒为 0 |

## 5. S4 收尾状态与 S5 前置

**已收**：D4-1 ~ D4-13 全部交付；标签、保护、模板、扫描齐备。

**待开发者审定**：`ITER-20261103-04`、`ITER-20261103-05`、本记录——三者审定后 S4 关账。

**S5 前置清单**（`GOV-000` §7）：阿里云资源开通、~~域名与 ICP 备案~~、staging/prod 编排、`OPS-001` 部署文档定稿、`RUN-002` 备份策略定稿、发布演练（标准 7 步）。

> 补记（2026-11-13）：~~域名与 ICP 备案~~ 已移出上线验收，改为上线后的可选拓展（`DEV-009` §7.8 T5-06）；清单其余各项已于 2026-11-12 前就绪或随执行段推进（`ITER-20261113-02`）。原文加删除线保留，口径以本补记为准。

## 6. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-03 | 初版建立：G4 十二项核对，2 项留痕豁免，1 处署名误标更正 | ITER-20261103-05 |
| 2026-11-13 | 按「只增不删可改」处置：S5 前置清单中「域名与 ICP 备案」加删除线并追加补记，说明其移出上线验收 | ITER-20261113-03 |
