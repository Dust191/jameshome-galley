---
title: 编码约定
doc_id: DEV-002
status: in_review
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-01
updated: 2026-11-01
next_review: 2027-01-30
iter: ITER-20261101-09
related: [ADR-0002, ADR-0003, DEV-003, CONTRIBUTING]
---

# DEV-002 编码约定

这份文档回答一个问题：**写代码时哪些事有统一答案，不必每次商量。**

与 `CONTRIBUTING.md` 重叠的部分不重复抄写，本文只写差异与实现细节。

## 1. 目录与分层

```
backend/src/main/java/io/github/jameshome/galley/
├── common/            # 通用能力：异常、Web、时间、ID 生成
├── modules/
│   ├── identity/      # 账号、邀请码、认证
│   ├── material/      # 物料、分类、单位
│   ├── stock/         # 流水、结存、红冲
│   └── stats/         # 统计
└── GalleyApplication.java
```

每个模块内部固定四层，依赖只能由外向内：

```
web（Controller/DTO） → application（Service/事务边界） → domain（实体/规则） → infrastructure（Mapper/仓储实现）
```

| 规则 | 判据 |
|---|---|
| `domain` 不得依赖 Spring、MyBatis 等框架 | ArchUnit `domainMustNotDependOnFrameworks` |
| `web` 不得直接返回 DO | ArchUnit `controllersMustNotExposeDataObjects` |
| `modules/*/api` 不得依赖 `modules/*/infrastructure` | ArchUnit `apiMustNotReachInfrastructure` |
| `common` 不得依赖 `modules` | ArchUnit `commonMustNotDependOnModules` |

四条规则已落地为 `ArchitectureTest`，越界即测试失败。

## 2. 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 包 | 全小写，无下划线 | `modules.stock.application` |
| 类 | 大驼峰 + 固定后缀 | `MovementController`、`StockService`、`MaterialDO`、`MovementCreateDTO` |
| 表 | 蛇形复数 | `stock_movement` |
| 字段 | 驼峰；与 glossary 英文名一致（G-3） | `safetyStock` |
| 常量 | 全大写蛇形 | `MAX_EXPORT_ROWS` |
| 测试方法 | `方法_场景_期望` | `reverse_已红冲_抛业务异常` |

**类型后缀含义**：`DO` 数据库行、`DTO` 入参/出参、`VO` 只读视图（仅统计）、`Cmd` 动作指令（如 `ReverseCmd`）。同一概念不得同时出现两种后缀。

## 3. 代码风格

- 后端：Spotless + google-java-format，`AOSP` 缩进 4 空格；`./mvnw spotless:apply` 自动修。
- 前端：Prettier（配置在 `frontend/.prettierrc`）+ ESLint flat config；`pnpm exec prettier --check .` 必须通过。
- 不提交注释掉的代码；不提交 `TODO` 而无对应技术债条目（见 `DEV-004`）。
- 中文注释允许且鼓励用于"为什么"，禁止用注释复述代码"做了什么"。

## 4. 分支与提交

| 项 | 约定 |
|---|---|
| 主分支 | `main`，只接受 squash 后的单提交 |
| 分支命名 | `feat/<主题>`、`fix/<主题>`、`docs/<主题>`、`chore/<主题>` |
| 提交格式 | Conventional Commits：`<type>(<scope>): <subject>` |
| type | `feat` `fix` `docs` `refactor` `test` `chore` `perf` `build` `ci` |
| scope | 模块名或对象名：`stock`、`identity`、`infra`、`docs`、`ci` |
| 必填尾注 | `Refs: <ITER 编号或 issue>`、`Signed-off-by: <姓名> <邮箱>` |
| 可选尾注 | `Assisted-by: <模型名-版本>`，无法确认标识时留空 |
| 长度 | subject ≤50 字符，正文每行 ≤72 字符 |

提交信息由 `scripts/check-commit-message.sh` 校验，署名由 `scripts/check-signoff.sh` 校验，两者都在 CI 里跑。

## 5. 本地模拟 PR（单人期过渡）

团队规模为 1 时以本地流程替代 GitHub 保护：

1. 从 `main` 切分支；
2. 按 `.github/PULL_REQUEST_TEMPLATE.md` 自审；
3. 冷却后再看一遍 diff；
4. `git merge --squash` 回 `main`，单提交落地；
5. 删除分支。

失效条件：出现第二位贡献者即恢复强制 review 与真实 PR。

## 6. 数据库变更

- 一切表结构变更走 Flyway，文件命名 `V<版本>__<描述>.sql`，**已执行过的脚本不得修改**。
- 每个变更脚本必须对应 `DES-001` 的更新；文档未更新视为未完成。
- 破坏性变更（删列、改类型）须拆成两步：先加新、再删旧，中间隔一个版本。

## 7. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-01 | 初版建立 | ITER-20261101-09 |
