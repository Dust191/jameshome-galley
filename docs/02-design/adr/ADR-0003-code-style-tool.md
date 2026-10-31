---
title: 代码规范工具只上 Spotless
doc_id: ADR-0003
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-10-31
updated: 2026-10-31
next_review: 2027-01-29
iter: ITER-20261031-02
related: [ADR-0002, NFR-001]
---

# ADR-0003 代码规范工具只上 Spotless

这份文档回答一个问题：**格式与风格由谁说了算，为什么不同时上 Checkstyle。**

- **状态**：accepted（依据决策 D-09：仅上 Spotless，须走 ADR）

## 1. 背景

GOV-000 §3.3 D1-4 的表述是「Spotless/Checkstyle 零告警」，未指定用哪一个。两者职责重叠：Spotless 会**改写**代码到统一格式，Checkstyle 会**报告**风格违规但不改。同时上会出现循环——Checkstyle 报出的问题 Spotless 已经改掉，或 Spotless 的格式化结果触发 Checkstyle 规则，形成"改不完"。

## 2. 选项与代价

| 选项 | 代价 |
|---|---|
| **仅 Spotless** | 只管格式，不管命名、圈复杂度、魔法值等风格规则；这些需靠 review 与 ArchUnit 兜住 |
| 仅 Checkstyle | 只报告不修复，需要人工逐条改；且默认规则集与主流格式化风格冲突多，噪音大 |
| 两者同时上 | 需要手工维护一份与格式化器兼容的 Checkstyle 规则集，成本高，且容易出现互相触发 |
| 都不上 | 格式靠编辑器与自觉，diff 噪音大，review 效率低 |

**决策**：仅 Spotless。

## 3. 决策

| 范围 | 工具 | 规则 |
|---|---|---|
| 后端 Java | Spotless + google-java-format（AOSP 变体不用，采用默认 2 空格以外的 4 空格配置需显式声明） | `make lint` 执行 `spotless:check`，CI 同 |
| 前端 TS/TSX/CSS | Prettier | 与 ESLint 分工：Prettier 管格式，ESLint 管正确性 |
| 提交前 | 本地钩子（Husky 或等价机制）执行 `make lint` 的快速子集 | 与 commitlint 串联 |

补充约定：

- Spotless 的 `target` 绑定在 `validate` 阶段，`make build` 与 CI 都会跑到，不依赖开发者手动执行。
- 不以"格式化会破坏我的排版"为由跳过；如需保留特定格式，使用 `// spotless:off` / `// spotless:on` 并在此处写一行理由。
- 命名、分层、模块边界等"风格之外"的约束由 ArchUnit 与 review 承担，不由 Spotless 承担。

## 4. 后果

**好的**：格式争议一次性消失；diff 噪音低，review 只看逻辑；工具链少一个，CI 快。

**坏的**：命名规范、魔法值、超长方法这类问题不再被自动拦下，完全依赖 §9.3 的自审清单与人工 review；这对单人开发是真实风险。

**需要后续偿还的**：若后续出现反复的命名或结构问题，可评估引入 Checkstyle 的**极小规则集**（仅命名与 import 顺序），但仍须避免与 Spotless 重叠；届时应新增 ADR 声明本决策被取代，而不是直接改本文件。
