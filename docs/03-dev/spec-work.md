---
title: 作业规范
doc_id: DEV-008
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-10
updated: 2026-11-13
next_review: 2027-02-11
iter: ITER-20261113-02
related: [DEV-002, DEV-003, DEV-004, DEV-006, DEV-007, GOV-002, GOV-005, GLOSSARY-001, INDEX-001, CONTRIBUTING]
---

# DEV-008 作业规范

这份文档回答一个问题：**一轮变更从想法到合并，每一步按什么做、做到什么算过、机器能查什么、人该看什么。**

它覆盖 GOV-000 中跨阶段长期有效的规则：人机协作、文档、代码、提交、评审、质量门、依赖与偏离。阶段相关的交付物与门验收在 `DEV-009`，产品边界在 `DEV-006`。

## 1. 人机协作九条

上线前全部开发为单人开发、人机协助：开发者是唯一决策主体与责任主体，模型承担起草、生成、核对、检查。九条约定没有"建议"性质，违反即按最后一列处置。

| 编号 | 约定 | 本仓执行动作 | 判定与处置 |
|---|---|---|---|
| H1 | 决策权归属 | 产品定位、范围边界、技术选型、数据模型、对外承诺五类只由开发者决定；模型列选项与代价，不代决 | 把"建议"写成"已定"即回退，决策重做 |
| H2 | 不得造词 | 写文档、代码标识符、界面文案、提交信息前先查 `GLOSSARY-001`；新概念提 `proposed` 词条 | 模型自转 `active` 即驳回（G-5） |
| H3 | 变更留痕 | 每轮一份迭代记录，含变更清单、变更过程、影响面、审定回顾 | 缺记录该轮不计进度 |
| H4 | 审定前置 | 未审定的产出不合并不部署，不作为下一轮输入；"模型说可以"不构成审定 | 立即回滚 |
| H5 | 可解释 | 非显然的设计写明理由；解释不了的实现退回 | 回退重做 |
| H6 | 事实核验 | 版本号、API 行为、依赖特性以官方来源或实测为准，不凭印象 | 视为缺陷 |
| H7 | 边界自觉 | 范围外需求登记到 `SCOPE-001` 参考方向，不预埋字段 | 按红线 2 处理 |
| H8 | 署名可溯 | `Assisted-by` 只写真实且在册的标识；无法确认就整行省略 | 缺失不算违规，虚构算违规（红线 13） |
| H9 | 人在回路 | 每轮动手前先 `git diff` 与 `git status` 读手改；不得以"上次提交状态"为前提覆盖 | 覆盖手改即失控，回退重做 |

### 1.1 最容易破的四条

| 条目 | 为什么会破 | 本仓的硬约束 |
|---|---|---|
| H1 | 模型顺手把"A 比 B 好"写成"A 已定" | 文档里出现"决定/采用"字样时，必须能指到开发者的原话或裁定记录；指不到就是越权 |
| H2 | 新词看起来"大家都懂"，不查就用 | `check-docs.mjs` 只拦禁用写法，不拦新词；新词靠评审与自审⑦兜底，漏一次就要补登记 |
| H4 | 一轮做完想直接进下一轮 | 每份新文档 `in_review` 起，未审定不得被下一轮引用为已定事实 |
| H9 | 工作区被手改后，模型按记忆重写整个文件 | 每轮第一条命令固定是 `git status --short` + `git diff --stat`；有手改先读再改 |

### 1.2 署名（H8 的完整规则）

| 编号 | 规则 |
|---|---|
| A-1 | 只写真实标识：取自运行时明确声明或厂商官方发布信息，不推测、不简写、不沿用上一轮 |
| A-2 | 必须含可区分版本，格式 `<模型名>-<主版本>[-<变体>]`，如 `DeepSeek-V4.1-Flash`；只写厂商名或"AI"无效 |
| A-3 | 无法确认则不写：不得填 `unknown`、`N/A`、`AI`、`assistant` 等占位词 |
| A-4 | 模型切换即换值，自切换后首条提交起生效 |
| A-5 | 值必须在 `CONTRIBUTORS.md` 模型协助登记表中存在，`check-signoff.sh` 强制校验 |
| A-6 | 不转移责任：`Assisted-by` 仅为溯源标注，作者责任不因标注而减轻 |

## 2. 文档体系

### 2.1 文档地图

| 位置 | 内容 | 主要读者 |
|---|---|---|
| `docs/00-index.md` | 文档清单、编号占用、状态总览 | 所有人 |
| `docs/00-glossary.md` | 词条、禁用写法、易混提示 | 所有人 / 模型 |
| `docs/01-product/` | 需求、范围、非功能基线 | 产品与开发 |
| `docs/02-design/` | 领域模型、接口契约、界面规范、指标口径、ADR | 开发 |
| `docs/03-dev/` | 搭建、编码约定、测试、技术债、验收、工程规范、偏离 | 开发 |
| `docs/04-ops/` | 部署、运行手册、备份恢复 | 运维与值班 |
| `docs/05-governance/` | 治理、发布、阶段门记录、标签、许可、工程规范总纲 | 维护者 |
| `docs/06-meeting/rfc/` | 重大变更提案 | 团队 |
| `docs/07-iteration/` | 迭代记录，每轮一份 | 开发者 |
| `docs/_templates/` | 七份模板，不参与校验 | 所有人 |

### 2.2 front-matter

`docs/` 下每份文档（模板目录除外）必须有十个字段，缺一即 CI 失败：

| 字段 | 规则 |
|---|---|
| `title` | 中文标题，与正文一级标题一致 |
| `doc_id` | 全局唯一，`<前缀>-<序号>`；ADR 四位，ITER 用日期 |
| `status` | `draft` / `in_review` / `accepted` / `rejected` / `approved` |
| `owner` | 具体人，禁止写团队 |
| `reviewers` | 至少一人，单人期为 owner 本人 |
| `created` / `updated` | `YYYY-MM-DD`；`updated` 随每次实质修改更新 |
| `next_review` | 默认 `updated + 90 天` |
| `iter` | 指向最后一次修改该文档的迭代记录，必须存在 |
| `related` | 交叉引用；只允许指向存在的文档编号或外部标识（`GOV-000`、`CONTRIBUTING`、`GOVERNANCE`、`SECURITY`） |

`status` 流转：`draft` → `in_review` → `approved`；被驳回时置 `rejected` 并退回 `draft`。`accepted` 保留给"事项已定、文档待发布"的中间态，当前未使用。

> 与 GOV-000 §4.4.3 的差异：该节写 `draft | review | approved | deprecated`，与同文件 §4.5 约束 7 的五个取值自相矛盾。本仓采用 §4.5 的取值（`ITER-20261101-02` 裁定）；文书废弃时不设 `deprecated`，改为在索引标注"已废弃"并保留编号。

### 2.3 两种「状态」不得混用

`status`（front-matter）描述**文档本身**处在审定的哪一步；正文首段的「状态」描述**所记事项**（决策、提案、问题）的裁定结果。前者取值见 §2.2，后者由各文档自定；不得用「文档状态」指称正文，也不得用 `status` 指称决策结果。

### 2.4 编号与前缀

任何新前缀须先在 `GLOSSARY-001` §2.6 登记；编号一经使用不回收。

| 前缀 | 用途 | 格式 | 不得用于 |
|---|---|---|---|
| `INDEX` / `GLOSSARY` | 顶层治理文件 | INDEX-001 | — |
| `PRD` / `SCOPE` / `NFR` | 产品文档 | PRD-001 | — |
| `DES` | 设计（模型/接口/UI/指标） | DES-004 | — |
| `ADR` | 架构决策 | ADR-0001 | — |
| `DEV` | 开发规范文档 | DEV-008 | 偏离条目 |
| `DVT` | 偏离登记条目 | DVT-01 | 文档编号 |
| `OPS` / `RUN` | 运维与运行手册 | RUN-001 | — |
| `GOV` | 治理文件 | GOV-006 | — |
| `RFC` | 重大变更提案 | RFC-0001 | — |
| `ITER` | 迭代记录 | ITER-20261110-01 | — |
| `MTG` | 会议纪要 | MTG-20261110 | — |
| `D-xx` / `L-xx` / `P-xx` / `E-xx` / `R-xx` | 仅存在于迭代记录内的决策、遗留、问题、待登记词条、待修订点 | D-01 | 文档编号 |

### 2.5 写作规则

| 规则 | 要求 |
|---|---|
| 结论先行 | 开头三行内说明"这份文档回答什么问题" |
| 一屏一节 | 单节不超过两屏；超过则拆子文件并在索引登记 |
| 图用文本 | 架构图、流程图、状态机用 Mermaid 源码，禁止只放图片 |
| 术语一致 | 只用 `active` 词条；禁用写法零容忍 |
| 可执行 | 步骤类内容必须能被他人照做成功，含预期输出 |
| 不写空话 | 禁止"高性能""易扩展"等无口径形容词，必须给数值或判据 |
| 中文标点 | 正文用中文标点；代码、路径、命令用半角 |

### 2.6 词条（GLOSSARY-001）

词条九字段：中文词条、英文名、定义、所属域、禁用写法、易混提示、状态、引入版本、示例。规则 G-1 ~ G-10 见 `GLOSSARY-001` §1，要点：

- 一个概念只有一个正式词条，其他写法登记为禁用写法；定义不得引用未登记词条。
- 代码标识符与英文名一致；界面文案只用中文词条。
- 新词条由模型提 `proposed`，开发者确认后转 `active`；模型不得自转。
- 「易混提示」只告警，不失败；「禁用写法」出现即失败。
- 「示例」必须用该词条造一个句子；变更记录按时间向下追加，保留开发者手写行。

### 2.7 迭代记录

轮次边界：一次集中变更对应一轮——① 一次决策确认后的连带修改；② 一个 PR 批次；③ 一次缺陷修复或规范调整。不得把连续多轮合并成一份。

定位（2026-11-13 开发者裁定）：迭代记录是**执行记录**——记当轮改动内容、当前遗留问题与后续需改进事项。章节层次照模板走，可拓展、不可偏离或缺失，「遗留项」一节必填；开头直接写本轮做了什么，§2.5「结论先行」的「回答什么问题」句式对迭代记录不适用。

必填六节：

| 节 | 内容 |
|---|---|
| 轮次信息 | 编号、日期、触发来源、涉及版本、参与模型、开发者 |
| 变更清单 | 逐条列文档编号与路径、代码模块、配置项，标注新增/修改/删除 |
| 变更过程 | 步骤、工具与模型、遇到的问题与处理、验证手段与结果 |
| 影响面 | 关联文档、接口、数据表、测试、部署配置 |
| 开发者审定回顾 | 审定人、时间、结论（通过/有条件通过/驳回）、逐项意见、遗留项 |
| 与上轮的差异摘要 | 三行以内 |

时限与不可变：完成后 24 小时内补记；只增不改，发现错误追加"补记"段；未审定的轮次产出不得进入下一轮。

**模型偏离须入账**：本轮若出现「规范有明确要求、产出未满足、能指出具体条款与产出」，须在 `GOV-007` 登记 `MDV` 条目，与本记录同批提交。只修现象不加防复发机制，状态一律记「未闭环」。

### 2.8 模板

| 模板 | 用于 | 额外要求 |
|---|---|---|
| `_template-doc.md` | 产品、治理等通用文档 | 开头三行说明回答什么问题 |
| `_template-design.md` | DES-001 ~ DES-004 | 每条结论可追到 PRD 故事或词条 |
| `_template-dev.md` | DEV 系列 | 步骤可照做，含预期输出 |
| `_template-ops.md` | OPS / RUN | 面向凌晨三点被叫醒的人，不可逆操作写回滚 |
| `_template-adr.md` | ADR | 状态栏与 `status` 分开写 |
| `_template-iter.md` | 迭代记录 | 六节 + 审定三行 |
| `_template-rfc.md` | RFC | 摘要、现状、方案对比、影响面、风险与回退 |

新建文档先复制模板，再删写作提示注释；不得从旧文档复制后改标题。

### 2.9 唯一门禁

`node scripts/check-docs.mjs` 是文档结构的唯一门禁，检查八项：

| 检查 | 失败条件 |
|---|---|
| front-matter | 缺分隔块或缺任一必填字段 |
| `status` | 取值不在五个允许值内 |
| `doc_id` | 重复 |
| `related` | 指向不存在的文档编号（外部标识除外） |
| `iter` | 指向的迭代记录不存在 |
| 索引 | `doc_id` 未在 `00-index.md` 出现 |
| 表格 | 表体被空行断开 |
| 用词 | 命中禁用写法；易混提示只告警 |

正文中确需逐条引用禁用写法做分析时，在文档首部声明豁免标记（把 `term-check` 置为 `off`）并写明理由；`docs/07-iteration/` 下的历史记录命中降为告警（只增不改）。CI 的 `guards` job 跑同一条命令，本地不过就是 CI 不过。

### 2.10 篇幅、复核与已知缺口

- `docs/` 内单节 ≤2 屏；根目录的 `README`、`CONTRIBUTING`、`GOVERNANCE`、`SECURITY`、`CODE_OF_CONDUCT` 是对外入口，必须自包含，不受"`docs/` 外 Markdown ≤100 行"限制——这是对本仓实际形态的明确修订，随本文件一并审定。
- `next_review` 到期后由人工在季度复核时处理；CI 不做自动标记。
- Markdown 链接可达性未纳入门禁；链接靠评审与季度复核发现。两项工具缺口见 `DEV-004` 的 `TD-17`。

## 3. 代码与数据

### 3.1 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| Java 包 | 反向域名 + 项目，小写 | `io.github.jameshome.galley.modules.stock` |
| 类 / 方法 | 大驼峰 / 小驼峰 | `MovementService`、`calculateBalance` |
| 常量 | 全大写下划线 | `MAX_PAGE_SIZE` |
| 数据库表 | 小写下划线，单数 | `material`、`stock_movement` |
| 数据库字段 | 小写下划线 | `created_at`、`safety_stock` |
| API 路径 | 小写复数，短横线分隔 | `/api/v1/materials`、`/api/v1/stock-movements` |
| 环境变量 | 全大写，前缀 `GALLEY_` | `GALLEY_DB_URL` |
| 前端文件 | 组件用大驼峰，其余小写短横线 | `MaterialListPage.tsx`、`lib/format.ts` |
| 分支 | `<类型>/<issue 或 ITER 号>-<短描述>` | `feat/42-csv-import` |
| 版本标签 | `v<主>.<次>.<修订>` | `v0.1.0` |

### 3.2 分层与模块边界

```
modules/<domain>/
├─ api/              # Controller、DTO、入参校验
├─ application/      # 用例编排、事务边界
├─ domain/           # 领域对象与规则，不依赖框架
└─ infrastructure/   # DO、Mapper、转换器、外部适配
```

四条 ArchUnit 规则（`ArchitectureTest`，ArchUnit 1.5.1，空集即失败）：

| 规则 | 说明 |
|---|---|
| `domain` 不依赖框架 | 不得依赖 Spring、MyBatis-Plus、JPA |
| `common` 不依赖 `modules` | 防止反向耦合与环 |
| `api` 不直达 `infrastructure` | 接口层只能经 `application` 编排 |
| Controller 不暴露 `*DO` | 必须经 DTO 转换 |

### 3.3 持久层

| 规则 | 本仓实现 | 差距 |
|---|---|---|
| 主键用 UUID v7（时间有序） | `UuidV7.generate()` 在应用层显式生成 | 无 |
| 单表 CRUD 用 `BaseMapper`，多表与统计写 XML | 一致；统计 SQL 在 `resources/mapper/*.xml` | 无 |
| 集合查询必须分页，单页上限 100 | `MaterialService` / `BalanceService` 以 `MAX_PAGE_SIZE = 100` 钳制，SQL 显式 `LIMIT/OFFSET` | 未使用 `PaginationInnerInterceptor`；规则以结果为准，写法不同不记偏离 |
| 删除只做软删，查询默认过滤 | 服务层写 `deleted_at`，查询显式带 `deleted_at IS NULL` | 未用 `@TableLogic`；同上，属写法差异 |
| 审计字段自动填充 | `created_at` / `updated_at` 由服务层赋值 | `created_by` / `updated_by` 未填，见 `DVT-08` |
| 乐观锁冲突返回 409 | `@Version` + `OptimisticLockerInnerInterceptor`；影响行数 0 抛 `VERSION_CONFLICT` | 无 |
| 参数一律 `#{}`；`${}` 仅用于白名单排序 | `orderBy` 由服务层白名单拼出 | 无 |
| 迁移脚本只前滚，已执行不得修改 | Flyway `V1` ~ `V3`；破坏性变更拆两步 | 无 |

### 3.4 数据规则

| 项 | 规则 |
|---|---|
| 时间 | 统一 `timestamptz` 存 UTC，展示按 `Asia/Shanghai` |
| 数量 | `numeric(14,3)`，禁止浮点；不做单位换算 |
| 结存更新 | 只允许原子增减（`INSERT ... ON CONFLICT DO UPDATE`），禁止"先读再写" |
| 流水 | 只增不改；数据库触发器拒绝删除，只允许更新 `reversed` |
| 枚举 | 数据库用 check 约束字符串；接口解析大小写不敏感，非法值归 400 |
| 索引 | 外键必建；`stock_movement(material_id, occurred_at desc)`；物料编码部分唯一索引（软删行不占码） |
| 并发 | 登记流水与更新结存在同一事务；结存用原子 SQL，物料编辑用乐观锁 |

### 3.5 接口

| 项 | 规则 |
|---|---|
| 版本 | 路径前缀 `/api/v1`；破坏性变更升 `v2`，旧版至少保留一个 minor |
| 资源 | 复数名词、短横线；层级 ≤2 |
| 方法 | GET 幂等；POST 创建；PATCH 局部更新；DELETE 软删；未实现的 PUT 不占位 |
| 状态码 | 200/201/204 成功；400 参数；401 未认证；403 无权限；404 不存在；409 冲突；422 业务拒绝；429 限流；500 服务错 |
| 错误体 | `{ code, message, field?, traceId }`；`code` 为大写下划线常量 |
| 分页 | `?page=1&pageSize=20`，响应 `{ items, page, pageSize, total, capped }`；`pageSize` 超上限时按 100 截断并置 `capped` |
| 过滤排序 | `?categoryId=&stockState=low&sort=-updatedAt`；前缀 `-` 表降序 |
| 幂等 | 流水登记请求体带 `idempotencyKey`（≤64 字符），同键重复提交返回首次结果，未传键不做幂等（`V3__movement_idempotency.sql` 唯一索引兜底） |
| 契约 | `contracts/openapi.yaml` 由 springdoc 生成后提交，`check-contract.sh` 与运行实例逐字比对 |

### 3.6 前端

- Prettier + ESLint 统一格式，`pnpm build` 内含 `tsc --noEmit`，不手工调格式。
- 对外文案集中在 `lib/format.ts`（密码、冲正、入库/出库/盘点调整、启用/停用/已归档），组件不得自造同义写法。
- 数量输入统一用 `QtyInput`：输入即同步表单，失焦只做归一化提示；合法值不得被静默改写。
- 请求统一走 `lib/api.ts`；下载走 Blob；401 保留 `ApiError` 以便页面提示。
- 只读页面优先适配移动端浏览器：表格横向滚动，关键字段不折叠。

## 4. 分支、提交与署名

### 4.1 分支

| 分支 | 用途 | 存续时间 | 规则 |
|---|---|---|---|
| `main` | 唯一长期分支，始终可发布 | 永久 | 保护；Squash 合并；线性历史 |
| `feat/*` `fix/*` `docs/*` `chore/*` | 日常开发 | ≤5 天 | 从 `main` 切出，合并后删除 |
| `release/<主>.<次>` | 版本维护（1.0 后启用） | 至该 minor EOL | 只接受 cherry-pick 的修复 |
| `hotfix/<版本>` | 生产紧急修复 | ≤24 小时 | 修完同时回合 `main` |

### 4.2 提交信息

`<type>(<scope>): <subject>`，subject 用中文、≤50 字、不加句号。

| type | 用途 |
|---|---|
| `feat` / `fix` / `refactor` / `perf` | 功能、修复、重构、性能 |
| `docs` / `test` / `build` / `ci` / `chore` / `revert` | 文档、测试、构建、流水线、杂务、回滚 |

scope 白名单：`api` `web` `db` `auth` `material` `stock` `stats` `infra` `docs` `deps` `release`。超出须先在 `CONTRIBUTING` §4 与 `commitlint.config.cjs` 同步增补。

### 4.3 trailer

```
feat(stock): 出库数量超过结存时拦截并提示可用量

- 结存不足返回 422，响应体带 availableQuantity

Refs: #42
Assisted-by: DeepSeek-V4.1-Flash
Signed-off-by: Dust <3941305663@qq.com>
```

| trailer | 必填 | 说明 |
|---|---|---|
| `Signed-off-by` | 是 | DCO 签署，须与 commit author 一致 |
| `Refs` / `Closes` | 是 | 关联 issue；建仓前的历史提交以 ITER 编号代替 |
| `Assisted-by` | 否 | 填写即须真实且在册；无法确认则整行省略 |
| `Co-authored-by` | 否 | 仅用于人类协作者，禁止用于模型 |

### 4.4 署名分层

| 层 | 载体 | 写法 |
|---|---|---|
| 对外展示 | README、LICENSE、NOTICE、Release、站点、演示材料 | `The JamesHome Contributors`（团队名 `JamesHome`） |
| 技术留痕 | commit `Author`、`Signed-off-by`、`Assisted-by` | `Dust <3941305663@qq.com>`；模型按 §1.2 |
| 明细登记 | `CONTRIBUTORS.md` | 人类贡献者表 + 模型协助登记表 |

具体规则：

| 编号 | 规则 |
|---|---|
| T-1 | 单人期即启用团队署名，不因只有一人而对写个人名义 |
| T-2 | 对外文案只出现团队名；唯一例外是 `LICENSE` 的 `Original Author` 行 |
| T-3 | 个人身份只出现在 Git 提交元数据与 `CONTRIBUTORS.md` |
| T-4 | 模型名称只出现在 `Assisted-by`、`CONTRIBUTORS.md`、迭代记录三处 |
| T-5 | 团队名固定 `JamesHome`，集体署名固定 `The JamesHome Contributors` |
| T-6 | 新增对外材料沿用同一署名，不得临时发明写法 |
| C-1 | `CONTRIBUTORS.md` 必须有署名段与两张表 |
| C-2 | 模型标识只能取自登记表，登记须附可核查来源 |
| C-3 | 外部贡献者的 PR 若由模型协助，须在描述声明并补登记 |
| C-4 | 合并前核对 `Assisted-by` 是否在册、是否与迭代记录一致 |
| C-5 | 禁止"感谢 AI 协助"这类无标识表述 |
| C-6 | 模型停用后保留旧行并标注日期，历史提交不回改 |
| C-7 | 对外文本不得用个人名替代团队署名 |

### 4.5 校验链

| 环节 | 命令 | 现状 |
|---|---|---|
| 提交信息 | `bash scripts/check-commit-message.sh <range>` | CI `guards` 执行；豁免合并提交与机器人作者（口径与署名校验一致，`DVT-07` 已收回） |
| 署名 | `bash scripts/check-signoff.sh <range>` | CI `guards` 执行；豁免机器人与合并提交（`DVT-05`） |
| 提交规则配置 | `commitlint.config.cjs` | 与脚本规则同源；无本地钩子 |
| 环境变量 | `bash scripts/check-env-example.sh` | 四组校验：开发模板对 `application.yml`、生产模板对 `compose.prod.yml`，另查含空格值是否加引号、文件是否带 CR |
| 生产编排 | `docker compose --env-file infra/compose/.env.prod.example -f infra/compose/compose.prod.yml config -q` | CI `guards` 执行；模板缺项、插值写错在此拦下，不必等真机启动 |
| 迭代记录结构 | `node scripts/check-docs.mjs` | 第一节须为「轮次信息」且六字段齐全；自 `ITER-20261112-10` 起六节齐全。规则来源 `GOV-007` M-01，更早记录只作历史 |
| 格式 | `make lint` | Spotless + Prettier + `check-docs` |

机器人提交按作者豁免（与署名校验同口径），其 `deps-dev` scope 与超长 subject 不再构成接入障碍；人类提交的格式在 CI 强制。

## 5. 评审

### 5.1 PR 必填十节

变更目的、变更范围、验证方式、影响面、文档影响、关联 issue、迭代记录编号、模型协助声明、自审十项、回滚判断。模板在 `.github/PULL_REQUEST_TEMPLATE.md`，缺节直接退回。

### 5.2 自审十项

① 边界（空值、超长、并发）；② 错误码复用既有常量；③ 新增查询有索引支撑；④ 写操作写审计；⑤ 补了对应测试；⑥ 更新受影响文档；⑦ 新词已登记；⑧ 迁移前滚兼容；⑨ 日志含 `traceId` 且不含敏感信息；⑩ 能在 5 分钟内回滚。

第 ④ 项自 2026-11-10 起可如实勾选：全部写路径经 `AuditService` 同事务落审计（`DVT-08` 已收回，`ITER-20261110-05`）；新增写路径时必须在对应服务埋点，否则第 ④ 项不得勾选。

### 5.3 评审规则

| 项 | 规则 |
|---|---|
| 批准人数 | 至少 1 名 Maintainer；`db/migration/`、`infra/`、`security` 相关需 2 名 |
| 首次响应 | ≤24 小时 |
| 合并 | 仅 Squash；禁止 merge commit；禁止 force push 到 `main` |
| 粒度 | 有效 diff ≤400 行；超过 800 行必须拆分或说明理由 |
| 阻塞 | CI 未绿、有未解决评论、分支落后、缺迭代记录、署名校验失败 |
| 意见分级 | `must` 不修不合；`should` 本轮或下轮修；`nit` 可不修 |

### 5.4 单人期的边界

GitHub 禁止作者自批，`main` 的分支保护因此设 `enforce_admins: false`、批准数 0（`DVT-04`）。补偿机制是：作者与合并之间至少间隔 12 小时；PR 描述必须走完自审十项；CI 全绿是硬条件。**"就我一个人"不构成跳过评审、迭代记录或发布检查单的理由**（`DEV-010` §9）。

## 6. 质量门

| 项 | 门槛 | 执行处 | 实测（0.1.0） |
|---|---|---|---|
| 行覆盖率 | 总体 ≥70%、领域层 ≥85% | `jacoco:check` 绑定 `verify` | 84.7% / 93.5%（接口层 99.4%） |
| 端点覆盖 | 契约内全部操作逐个覆盖 | `EndpointCoverageTest` 等 6 个测试类 | 41 个操作（32 条路径，含 `/healthz`） |
| 端到端 | 3 条主流程 | `frontend/e2e/flows.spec.ts` | 建档→入库→查看；冲正→回退；导入→错误报告 |
| 架构 | 4 条规则，空集即失败 | `ArchitectureTest`（ArchUnit 1.5.1） | 全绿 |
| 性能 | 列表 P95 ≤300ms、总览 P95 ≤500ms | 5 万物料 + 200 万流水实测 | 24ms / 251ms |
| 依赖漏洞 | 0 High / 0 Critical | osv-scanner + GitHub 告警 | 0 / 0（Moderate 4） |
| 结存对账 | 种子库零差异 | `GET /api/v1/reconcile` | `checked=50, mismatched=0` |
| 文档 | 结构校验全过、禁用写法零命中 | `scripts/check-docs.mjs` | 通过 |
| 技术债 | 入账有判据，偿还即迁移 | `DEV-004` | 在册 11 项、已偿 8 项 |

执行时机：本地提交前 `make lint && make test`；PR 由 `ci.yml` 三个 job 执行；发版前按 `GOV-002` §2 八项清单；每日 02:30 对账。门槛只升不降；下调须走 RFC 并在 `DEV-002` / `DEV-003` 同步。

## 7. 依赖治理

| 项 | 规则 |
|---|---|
| 许可 | 仅 Apache-2.0 / MIT / BSD / EPL；清单见 `GOV-005` |
| 版本 | 统一在父 `pom.xml` 的 `dependencyManagement` 声明，模块内不写版本 |
| 升级节奏 | 每周一次小版本批次；major 一律不进周更 |
| 分组 | Dependabot 按生态分组；`ignore` 21 条挡下 major |
| 机器人提交 | Squash 合并；不带 `Assisted-by`；签署豁免（`DVT-05`） |
| 漏洞扫描 | osv-scanner 在线查询 + GitHub 告警；OWASP 全量未执行（`DVT-06` / `TD-09`） |
| 新增依赖 | 说明用途、许可证、体积、替代方案；功能重叠的重型库不引入 |

## 8. 偏离登记

**何时记**：知道与规范不一致、暂时按现状走、且不一致有代价——三条同时成立才入账。已经合规的、纯粹"以后想优化"的、规范本身允许的取舍，都不记。

**条目六要素**：编号（`DVT-NN`，顺延不回收）、所违条款、实现现状、理由、影响、收回条件。收回后保留条目并标注日期，不删除。

**与规范的关系**：台账记的是现实；现实与本套冲突时以台账为准，并由台账反过来提出修订。

**在册条目**：生效 8 条——`DVT-01` JaCoCo 版本、`DVT-02` 格式化器版本、`DVT-04` 分支保护管理员通道、`DVT-05` 机器人签署豁免、`DVT-06` OWASP 未执行、`DVT-10` ECS 规格低于规范、`DVT-11` 自建库 RPO 24 小时、`DVT-13` Quick Tunnel 临时入口；已收回 5 条——`DVT-03`、`DVT-07`、`DVT-08`、`DVT-09`、`DVT-12`。编号已用到 `DVT-13`，下一号 14。明细见 `DEV-007`。

## 9. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-10 | 初版：从 GOV-000 §0.4 / §3.4 / §4.4 / §5.5 / §6.4 / §8.4 收敛为跨阶段作业规范，按本仓实现逐条标注差距 | ITER-20261110-01 |
| 2026-11-12 | §2.7 增补模型偏离入账要求；§4.5 增补迭代记录结构校验（`GOV-007` M-01 / M-03） | ITER-20261112-11 |
| 2026-11-13 | §2.7 增补迭代记录定位：执行记录，开头不套用「回答什么问题」句式 | ITER-20261113-01 |
| 2026-11-13 | §3.5 分页与幂等口径与实现对齐；§8 在册偏离条目按实况重列（生效 8 条、已收回 5 条、下一号 14） | ITER-20261113-02 |
