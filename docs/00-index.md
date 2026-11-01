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
| GLOSSARY-001 | `docs/00-glossary.md` | 总名词解释（46 条，全部 active） | approved | @Dust191 | 2026-11-01 | S0 |
| INDEX-001 | `docs/00-index.md` | 文档索引 | approved | @Dust191 | 2026-10-31 | S0 |
| PRD-001 | `docs/01-product/PRD-001.md` | 产品需求文档 | approved | @Dust191 | 2026-10-31 | S0 |
| SCOPE-001 | `docs/01-product/scope.md` | 范围冻结表 | approved | @Dust191 | 2026-10-31 | S0 |
| NFR-001 | `docs/01-product/nfr.md` | 非功能需求基线 | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0001 | `docs/02-design/adr/ADR-0001-tech-stack.md` | 技术栈选型 | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0002 | `docs/02-design/adr/ADR-0002-repo-layout.md` | 仓库目录结构与构建入口 | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0003 | `docs/02-design/adr/ADR-0003-code-style-tool.md` | 代码规范工具只上 Spotless | approved | @Dust191 | 2026-10-31 | S0 |
| ADR-0004 | `docs/02-design/adr/ADR-0004-jdk-pin-granularity.md` | JDK 版本锁定粒度 | approved | @Dust191 | 2026-11-01 | S1 |
| ITER-20261031-01 | `docs/07-iteration/ITER-20261031-01.md` | 首轮迭代记录（立项前置决策） | in_review | @Dust191 | 2026-10-31 | S0 |
| ITER-20261031-02 | `docs/07-iteration/ITER-20261031-02.md` | S0 文档体系建立 | in_review | @Dust191 | 2026-10-31 | S0 |
| ITER-20261031-03 | `docs/07-iteration/ITER-20261031-03.md` | S1 项目骨架搭建 | in_review | @Dust191 | 2026-10-31 | S1 |
| ITER-20261101-01 | `docs/07-iteration/ITER-20261101-01.md` | JDK 锁定粒度与词条补登记 | in_review | @Dust191 | 2026-11-01 | S1 |
| ITER-20261101-02 | `docs/07-iteration/ITER-20261101-02.md` | `status` 与「状态」口径统一 | in_review | @Dust191 | 2026-11-01 | S1 |
| GOV-000 | `.workbuddy/jameshome-galley-GOV-000-工程规范.md` | 工程规范 | approved（v1.9） | @Dust191 | 2026-11-01 | 全局 |

## 2. 编号占用

| 前缀 | 含义 | 已用 | 下一个 |
|---|---|---|---|
| INDEX | 索引 | 001 | 002 |
| GLOSSARY | 总名词解释 | 001 | 002 |
| PRD | 产品需求 | 001 | 002 |
| SCOPE | 范围 | 001 | 002 |
| NFR | 非功能 | 001 | 002 |
| DES | 设计（模型/接口/UI/指标） | — | 001 |
| ADR | 架构决策 | 0001 ~ 0004 | 0005 |
| DEV | 开发规范（文档编号） | — | 001 |
| DVT | 偏离登记（条目编号） | 01 ~ 03 | 04 |
| OPS / RUN | 运维与运行手册 | — | 001 |
| GOV | 治理 | 000（工程规范） | 001 |
| RFC | 重大变更提案 | — | 0001 |
| ITER | 迭代记录 | 20261031-01、20261031-02 | — |
| MTG | 会议纪要 | — | — |

## 3. 待建立的强制文档（S2 补齐）

S2 阶段门要求 19 份强制文档齐备。当前缺：`DES-001` 领域模型、`DES-002` 接口、`DES-003` UI 规范、`DES-004` 指标口径、`DEV-001` 本地搭建、`DEV-002` 编码约定、`DEV-003` 测试策略、`DEV-004` 技术债台账、`OPS-001` 部署、`RUN-001` 运行手册、`RUN-002` 备份恢复、`GOV-001` 治理、`GOV-002` 发布流程，以及索引与 glossary 之外的模板集（`_template.md` 系列）。

## 4. 已知位置问题

| 事项 | 说明 | 处置 |
|---|---|---|
| `GOV-000` 位于 `docs/` 之外 | GOV-000 §4.5 约束 1 要求 `docs/` 之外的 Markdown 不超过 100 行，而工程规范有 1600 余行 | **已裁定（2026-10-31）**：`GOV-000` 定位为「指导文档」，不是本项目的落地工程规范，不迁入 `docs/`、不归属本仓库；`.workbuddy/` 在 S1 建仓时由 `.gitignore` 排除。约束 1 仅适用于仓库内的 Markdown，故不构成违反。后续若需落地工程规范，须依当前已实现的内容与细节另写一份本地化版本，届时按普通文档登记入本索引 |

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
