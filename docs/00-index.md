---
title: 文档索引
doc_id: INDEX-001
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-10-31
updated: 2026-10-31
next_review: 2027-01-29
iter: ITER-20261031-02
related: [GLOSSARY-001, PRD-001]
---

# INDEX-001 文档索引

这份文档回答一个问题：**这个项目有哪些文档，各自的 `status` 是什么，谁负责。**
任何新增文档必须在本表登记，并从 `00-glossary.md` 取词。

## 1. 文档清单

| doc_id | 路径 | 标题 | `status` | owner | 更新 | 阶段 |
|---|---|---|---|---|---|---|
| GLOSSARY-001 | `docs/00-glossary.md` | 总名词解释（48 条，全部 active） | approved | @Dust191 | 2026-11-01 | S0 |
| INDEX-001 | `docs/00-index.md` | 文档索引 | approved | @Dust191 | 2026-10-31 | S0 |
| PRD-001 | `docs/01-product/PRD-001.md` | 产品需求文档 | approved | @Dust191 | 2026-10-31 | S0 |
| SCOPE-001 | `docs/01-product/scope.md` | 范围冻结表 | approved | @Dust191 | 2026-10-31 | S0 |
| NFR-001 | `docs/01-product/nfr.md` | 非功能需求基线 | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0001 | `docs/02-design/adr/ADR-0001-tech-stack.md` | 技术栈选型 | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0002 | `docs/02-design/adr/ADR-0002-repo-layout.md` | 仓库目录结构与构建入口 | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0003 | `docs/02-design/adr/ADR-0003-code-style-tool.md` | 代码规范工具只上 Spotless | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0004 | `docs/02-design/adr/ADR-0004-jdk-pin-granularity.md` | JDK 版本锁定粒度 | approved | @Dust191 | 2026-11-01 | S1 |
| ADR-0005 | `docs/02-design/adr/ADR-0005-object-storage.md` | 对象存储采用可替换实现 | approved | @Dust191 | 2026-11-02 | S3 |
| DES-001 | `docs/02-design/domain-model.md` | 领域模型 | approved | @Dust191 | 2026-11-01 | S2 |
| DES-004 | `docs/02-design/metrics.md` | 统计指标口径 | approved | @Dust191 | 2026-11-01 | S2 |
| DES-002 | `docs/02-design/api.md` | 接口契约 | approved | @Dust191 | 2026-11-01 | S2 |
| DES-003 | `docs/02-design/ui-spec.md` | 界面规范 | approved | @Dust191 | 2026-11-01 | S2 |
| DEV-001 | `docs/03-dev/setup.md` | 本地搭建 | approved | @Dust191 | 2026-11-01 | S2 |
| DEV-002 | `docs/03-dev/conventions.md` | 编码约定 | approved | @Dust191 | 2026-11-01 | S2 |
| DEV-003 | `docs/03-dev/testing.md` | 测试策略 | approved | @Dust191 | 2026-11-01 | S2 |
| DEV-004 | `docs/03-dev/tech-debt.md` | 技术债台账 | approved | @Dust191 | 2026-11-03 | S2 |
| DEV-005 | `docs/03-dev/uat-v0.1.md` | 验收记录 v0.1 | approved | @Dust191 | 2026-11-03 | S3 |
| DEV-006 | `docs/03-dev/spec.md` | 工程规范（仓内版） | in_review | @Dust191 | 2026-11-04 | S4 |
| DEV-007 | `docs/03-dev/deviations.md` | 偏离登记台账 | in_review | @Dust191 | 2026-11-04 | S4 |
| OPS-001 | `docs/04-ops/deploy.md` | 部署（S5 定稿，不参与 S2 审定） | in_review | @Dust191 | 2026-11-01 | S5 |
| RUN-001 | `docs/04-ops/runbook.md` | 运行手册 | approved | @Dust191 | 2026-11-01 | S2 |
| RUN-002 | `docs/04-ops/backup-restore.md` | 备份与恢复（S5 定稿，不参与 S2 审定） | in_review | @Dust191 | 2026-11-01 | S5 |
| GOV-001 | `docs/05-governance/governance.md` | 治理模型 | approved | @Dust191 | 2026-11-01 | S2 |
| GOV-002 | `docs/05-governance/release.md` | 发布流程 | approved | @Dust191 | 2026-11-03 | S2 |
| GOV-003 | `docs/05-governance/g3-freeze.md` | 阶段门 G3 通过记录与 0.1.0 冻结 | approved | @Dust191 | 2026-11-03 | S3 |
| GOV-004 | `docs/05-governance/labels.md` | 标签体系 | approved | @Dust191 | 2026-11-03 | S4 |
| GOV-005 | `docs/05-governance/third-party-licenses.md` | 第三方依赖许可证清单 | approved | @Dust191 | 2026-11-03 | S4 |
| GOV-006 | `docs/05-governance/g4-check.md` | 阶段门 G4 验收记录 | approved | @Dust191 | 2026-11-03 | S4 |
| ITER-20261031-01 | `docs/07-iteration/ITER-20261031-01.md` | 首轮迭代记录（立项前置决策） | approved | @Dust191 | 2026-10-31 | S0 |
| ITER-20261031-02 | `docs/07-iteration/ITER-20261031-02.md` | S0 文档体系建立 | approved | @Dust191 | 2026-10-31 | S0 |
| ITER-20261031-03 | `docs/07-iteration/ITER-20261031-03.md` | S1 项目骨架搭建 | approved | @Dust191 | 2026-10-31 | S1 |
| ITER-20261101-01 | `docs/07-iteration/ITER-20261101-01.md` | JDK 锁定粒度与词条补登记 | approved | @Dust191 | 2026-11-01 | S1 |
| ITER-20261101-02 | `docs/07-iteration/ITER-20261101-02.md` | `status` 与「状态」口径统一 | approved | @Dust191 | 2026-11-01 | S1 |
| ITER-20261101-03 | `docs/07-iteration/ITER-20261101-03.md` | GLOSSARY-001 改动失控与修正 | approved | @Dust191 | 2026-11-01 | S1 |
| ITER-20261101-04 | `docs/07-iteration/ITER-20261101-04.md` | 补充人机协作约定并同步派生表述 | approved | @Dust191 | 2026-11-01 | S1 |
| ITER-20261101-05 | `docs/07-iteration/ITER-20261101-05.md` | S2 启动：模板集、文档校验与领域模型 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261101-06 | `docs/07-iteration/ITER-20261101-06.md` | 采纳 Q-1~Q-3 并补登记两个词条 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261101-07 | `docs/07-iteration/ITER-20261101-07.md` | 编写 DES-004 指标口径 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261101-08 | `docs/07-iteration/ITER-20261101-08.md` | 回写指标裁定并编写接口与界面规范 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261101-09 | `docs/07-iteration/ITER-20261101-09.md` | 补齐 DEV/OPS/RUN/GOV 九份文档 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261101-10 | `docs/07-iteration/ITER-20261101-10.md` | S2 安全收尾与禁用写法问题分析 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261101-11 | `docs/07-iteration/ITER-20261101-11.md` | 起草 RFC-0001 禁用写法拆分 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261101-12 | `docs/07-iteration/ITER-20261101-12.md` | 实施 RFC-0001 并核对全部 ITER 遗留项 | approved | @Dust191 | 2026-11-01 | S2 |
| ITER-20261102-01 | `docs/07-iteration/ITER-20261102-01.md` | S3 开工：迁移验证与存储落点 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261102-02 | `docs/07-iteration/ITER-20261102-02.md` | T2 通用层与认证 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261102-03 | `docs/07-iteration/ITER-20261102-03.md` | 引导管理员与主数据字典 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261102-04 | `docs/07-iteration/ITER-20261102-04.md` | 物料主数据与图片上传，首次端到端联调 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261102-05 | `docs/07-iteration/ITER-20261102-05.md` | 出入库登记、红冲与结存 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261102-06 | `docs/07-iteration/ITER-20261102-06.md` | 九项统计指标 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261102-07 | `docs/07-iteration/ITER-20261102-07.md` | 物料 CSV 导出 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261102-08 | `docs/07-iteration/ITER-20261102-08.md` | 物料导入与流水导出 | approved | @Dust191 | 2026-11-03 | S3 |
| ITER-20261102-09 | `docs/07-iteration/ITER-20261102-09.md` | 前端返工：七项验收问题修复 | approved | @Dust191 | 2026-11-02 | S3 |
| ITER-20261103-01 | `docs/07-iteration/ITER-20261103-01.md` | 重启自检与验收前修正 | approved | @Dust191 | 2026-11-03 | S3 |
| ITER-20261103-02 | `docs/07-iteration/ITER-20261103-02.md` | 验收前修复：登录文案、缩略图、导出入口 | approved | @Dust191 | 2026-11-03 | S3 |
| ITER-20261103-03 | `docs/07-iteration/ITER-20261103-03.md` | T7/T8 收口：集成测试、对账、契约与发布 0.1.0 | approved | @Dust191 | 2026-11-03 | S3 |
| ITER-20261103-04 | `docs/07-iteration/ITER-20261103-04.md` | S4 建仓前置：开源配套文件核对与补齐 | approved | @Dust191 | 2026-11-03 | S4 |
| ITER-20261103-05 | `docs/07-iteration/ITER-20261103-05.md` | 首个 PR：DCO 豁免、依赖升级范围约束与 Issue 模板 | approved | @Dust191 | 2026-11-03 | S4 |
| ITER-20261104-01 | `docs/07-iteration/ITER-20261104-01.md` | GOV-000 落地：工程规范仓内版与偏离登记台账 | in_review | @Dust191 | 2026-11-04 | S4 |
| RFC-0001 | `docs/06-meeting/rfc/RFC-0001.md` | 收窄禁用写法并拆分易混提示 | approved | @Dust191 | 2026-11-01 | S2 |
| GOV-000 | `.workbuddy/jameshome-galley-GOV-000-工程规范.md` | 工程规范 | approved（v1.12） | @Dust191 | 2026-11-01 | 全局 |

## 2. 编号占用

| 前缀 | 含义 | 已用 | 下一个 |
|---|---|---|---|
| INDEX | 索引 | 001 | 002 |
| GLOSSARY | 总名词解释 | 001 | 002 |
| PRD | 产品需求 | 001 | 002 |
| SCOPE | 范围 | 001 | 002 |
| NFR | 非功能 | 001 | 002 |
| DES | 设计（模型/接口/UI/指标） | 001 ~ 004 | 005 |
| ADR | 架构决策 | 0001 ~ 0005 | 0006 |
| DEV | 开发规范（文档编号） | 001 ~ 007 | 008 |
| DVT | 偏离登记（条目编号） | 01 ~ 06 | 07 |
| OPS / RUN | 运维与运行手册 | 001、001 ~ 002 | 002 / 003 |
| GOV | 治理 | 000（指导文档）、001 ~ 006 | 007 |
| RFC | 重大变更提案 | 0001 | 0002 |
| ITER | 迭代记录 | 20261031-01 ~ 20261104-01（30 份） | — |
| MTG | 会议纪要 | — | — |

## 3. 强制文档齐备情况

S2 阶段门要求 19 份强制文档齐备，已于 2026-11-01 全部建立：

| 类别 | 编号 | 数量 |
|---|---|---|
| 顶层治理 | INDEX-001、GLOSSARY-001 | 2 |
| 产品 | PRD-001、SCOPE-001、NFR-001 | 3 |
| 设计 | DES-001 ~ DES-004 | 4 |
| 开发 | DEV-001 ~ DEV-004 | 4 |
| 运维 | OPS-001、RUN-001、RUN-002 | 3 |
| 治理 | GOV-001、GOV-002 | 2 |
| 迭代 | ITER-* | 按轮次递增 |
| 合计（不含 ITER） | — | 18 + ITER 类 |

S3 新增三份，不计入 S2 的 19 份强制清单：`DEV-005`（12 条 UAT 与工程质量门记录）、
`GOV-003`（G3 通过记录与 0.1.0 冻结）、`GOV-005`（第三方依赖许可清单，S4 起草）。
S4 新增 `GOV-004`（标签体系）、`GOV-006`（G4 验收记录）、`DEV-006`（工程规范仓内版）、`DEV-007`（偏离登记台账）。

模板集见 §6。`docs/idea.md` 为可选项，当前未建立，不参与阶段门。

## 4. 已知位置问题

| 事项 | 说明 | 处置 |
|---|---|---|
| `GOV-000` 位于 `docs/` 之外 | GOV-000 §4.5 约束 1 要求 `docs/` 之外的 Markdown 不超过 100 行，而工程规范有 1600 余行 | **已裁定（2026-10-31）**：`GOV-000` 定位为「指导文档」，不是本项目的落地工程规范，不迁入 `docs/`、不归属本仓库；`.workbuddy/` 在 S1 建仓时由 `.gitignore` 排除。约束 1 仅适用于仓库内的 Markdown，故不构成违反。**落地版本已于 2026-11-04 建立**：`DEV-006`（工程规范仓内版）+ `DEV-007`（偏离登记台账），GOV-000 保持指导文档定位 |

## 5. `status` 与「状态」

这两个词指的不是同一件事，引用时必须用对。规范原文见 GOV-000 §4.5 约束 7。

| 字段 | 位置 | 描述对象 | 取值流转 | 一句话说明 |
|---|---|---|---|---|
| `status` | front-matter | **文档本身** | `draft` → `in_review` → `accepted` → `approved`；`in_review` 被判 `rejected` 时退回 `draft` | 这份文档处在审定的哪一步 |
| 「状态」 | 正文首段 | 文档所记录的**事项**（决策、提案、问题） | `draft` → `pending` → `accepted` / `rejected` → `resolved` | 那个事项被怎么裁定 |

因此 `status: draft` 只表示「这份文档的表述还没经开发者审定」，**不代表**它记录的决策未定。示例：ADR-0001 的 `status` 为 `approved`（文档已发布、可被引用），正文「状态」为 `accepted`（决策已采纳）。

用词约定：

- 引用其它文档 front-matter 的字段时写 `status`，并用反引号包裹。
- 指代文档正文首段的内容时写「状态」。
- 不得用「文档状态」指称正文内容，也不得用 `status` 指称决策结果。

## 6. 模板集

新建文档一律复制对应模板，不得从旧文档复制后改标题。模板自身不参与 CI 校验（`_templates/` 已在 `check-docs.mjs` 中排除）。

| 模板 | 用于 | 额外要求 |
|---|---|---|
| `_templates/_template-doc.md` | 产品、治理等通用文档 | 开头三行内说明"回答什么问题" |
| `_templates/_template-design.md` | DES-001 ~ DES-004 | 每条结论须可追溯到 PRD 故事或 glossary 词条 |
| `_templates/_template-dev.md` | DEV-001 ~ DEV-007 | 步骤须可照做，含预期输出 |
| `_templates/_template-ops.md` | OPS-001、RUN-001、RUN-002 | 面向"凌晨三点被叫醒的人"，不可逆操作须写回滚 |
| `_templates/_template-adr.md` | ADR-0005 起 | 状态栏与 front-matter 的 `status` 分开写 |
