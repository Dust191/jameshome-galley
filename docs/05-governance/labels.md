---
title: 标签体系
doc_id: GOV-004
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-03
updated: 2026-11-03
next_review: 2027-02-03
iter: ITER-20261103-04
related: [GOV-000, GOVERNANCE, CONTRIBUTING]
---

# GOV-004 标签体系

这份文档回答一个问题：**仓库里有哪些标签、每个标签什么时候用、谁可以新增。**

## 1. 规则

| 编号 | 规则 |
|---|---|
| L-1 | 总数上限 **25**，当前 25 个已用满；新增必须删除一个或证明两个标签不可合并（`GOV-000` §6.5 约束 4） |
| L-2 | 命名统一为 `<维度>/<取值>`，中文仅出现在描述里；三个固定短语例外：`good first issue`、`help wanted`、`breaking-change` |
| L-3 | 每个 issue 必须恰好一个 `type/*`，triage 后必须恰好一个 `p*/*`，`status/*` 同时只能有一个 |
| L-4 | `area/*` 可多个，按受影响的模块打；跨模块改动不强求穷举 |
| L-5 | `good first issue` 必须附带「改哪里、怎么验证」，否则不得打（约束 5） |
| L-6 | 标签变更需 Maintainer 批准，并在本文 §7 记录 |

## 2. 类型（`type/*`）

每个 issue 必有一个，决定这件事的性质。

| 标签 | 颜色 | 定义 | 使用场景 |
|---|---|---|---|
| `type/bug` | `d73a4a` | 与文档或预期行为不符的缺陷 | 复现得出、能指出期望与实际差异的问题 |
| `type/feature` | `1d76db` | 新增能力或行为增强 | 用户可感知的新功能；内部重构不得用此标签 |
| `type/docs` | `0075ca` | 文档缺失、过期或与实现不符 | 契约、设计、开发文档的修正 |
| `type/refactor` | `fbca04` | 不改外部行为的结构调整 | 拆分模块、消除重复；改了行为就不算重构 |
| `type/security` | `b60205` | 安全缺陷或加固 | 认证、授权、注入、依赖漏洞；同时按 `SECURITY.md` 走私密渠道 |
| `type/question` | `d876e3` | 用法或设计意图的疑问 | 不产生代码变更的讨论；结论需沉淀时转 docs |

## 3. 领域（`area/*`）

按受影响的模块打，可多个。

| 标签 | 颜色 | 定义 | 使用场景 |
|---|---|---|---|
| `area/api` | `5319e7` | 后端接口与契约 | 端点、错误码、`contracts/openapi.yaml` |
| `area/web` | `006b75` | 前端页面与交互 | 路由、表单、组件、样式 |
| `area/db` | `0e8a16` | 表结构与迁移 | `db/migration/`、索引、约束 |
| `area/stock` | `c2e0c6` | 流水、结存、对账 | 登记、冲正、结存口径、对账任务 |
| `area/stats` | `c5def5` | 统计与预警 | 九项指标、趋势、预警清单、导出 |
| `area/infra` | `bfdadc` | 构建、部署、编排 | CI、Compose、Caddy、备份脚本 |
| `area/docs` | `fef2c0` | 文档体系 | `docs/` 下的结构与用词 |

## 4. 优先级（`p*/*`）

triage 后必有一个。

| 标签 | 颜色 | 定义 | 使用场景 |
|---|---|---|---|
| `p0/blocker` | `b60205` | 阻断使用或数据正确性受损 | 无法登录、结存算错、数据丢失；24 小时内响应 |
| `p1/high` | `d93f0b` | 核心路径受损但有绕过办法 | 导出失败、预警误报；3 个工作日内响应 |
| `p2/normal` | `fbca04` | 常规缺陷与计划内改进 | 默认档，7 个工作日内响应 |
| `p3/low` | `e4e669` | 体验瑕疵与边缘场景 | 不影响完成任务的排版、文案问题 |

## 5. 状态（`status/*`）

同一时间只允许一个。

| 标签 | 颜色 | 定义 | 使用场景 |
|---|---|---|---|
| `status/needs-triage` | `ededed` | 尚未分类 | 新 issue 默认状态，维护者补 type 与 priority 后移除 |
| `status/accepted` | `0e8a16` | 已确认，等待排期 | 结论明确但未开工 |
| `status/in-progress` | `fbca04` | 有人在做 | 关联 PR 打开后打，PR 合并或关闭时移除 |
| `status/blocked` | `d93f0b` | 被外部条件阻塞 | 等上游修复、等决策、等资源；描述里必须写明阻塞点 |
| `status/wontfix` | `ffffff` | 决定不处理 | 必须写明理由并允许复议一次 |

## 6. 引导（三个固定短语）

| 标签 | 颜色 | 定义 | 使用场景 |
|---|---|---|---|
| `good first issue` | `7057ff` | 适合首次贡献 | 改动范围清晰、有明确验证方式；必须附指引 |
| `help wanted` | `008672` | 欢迎外部参与 | 维护者短期内不会处理但认可其价值 |
| `breaking-change` | `e11d21` | 破坏兼容 | 接口、契约、数据结构的对外不兼容变更；须在 CHANGELOG 标注 |

## 7. 落地与变更

**创建**：仓库建立后执行

```bash
gh auth login                 # 首次需要
scripts/setup-labels.sh Dust191/jameshome-galley
```

脚本按本文表格逐条 `gh label create --force`，可重复执行；颜色与描述以本文为准。

**核对**：`gh label list --repo Dust191/jameshome-galley --limit 100 | wc -l` 应为 25（GitHub 默认标签需先删除，脚本会一并清理）。

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-03 | 初版建立：5 个维度 25 个标签，附创建脚本 | ITER-20261103-04 |
