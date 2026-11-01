---
title: 技术栈选型
doc_id: ADR-0001
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-10-31
updated: 2026-10-31
next_review: 2027-01-29
iter: ITER-20261031-02
related: [PRD-001, NFR-001, ADR-0002]
---

# ADR-0001 采用 JDK 25 + Spring Boot 4.1.2 + MyBatis-Plus 3.5.18 + PostgreSQL 18.3

这份文档回答一个问题：**为什么是这套组合，代价是什么，出问题时按什么顺序处置。**

- **状态**：accepted（依据 GOV-000 §1.5 技术基线）。
- **决策日期**：2026-10-30（GOV-000 v1.4 确立），2026-10-31 补入处置顺序（决策 D-10）

## 1. 背景

项目为单体分层应用，工作量一半在统计（窗口函数、按日分组、多表聚合），一半在单表写入（流水与结存）。部署形态是公有云单机自托管，只有一台 2C4G 的 ECS，不引入额外中间件。这两个特征共同约束了选型：**统计要能看得到、改得动 SQL；运维面要尽可能窄。**

## 2. 选项与代价

### 2.1 语言运行时

| 选项 | 代价 |
|---|---|
| **JDK 25.0.4.1（LTS）** | 新 LTS，部分第三方库的字节码与反射适配仍在跟进；需以 Toolchains 与 CI 镜像双重锁定，不能依赖本机默认 JDK |
| JDK 21（LTS） | 生态最稳，但虚拟线程与若干 API 的收益用不上；与本项目"用新基线避免中期迁移"的意图相反 |

**决策**：JDK 25.0.4.1。CI 与生产镜像同版本。

### 2.2 应用框架

| 选项 | 代价 |
|---|---|
| **Spring Boot 4.1.2** | 与 JDK 25 同代；生态最全；但 4.x 尚新，个别 starter 的自动配置可能与 3.x 行为有差异 |
| Spring Boot 3.x | 最保守，但为后续迁移埋下一次大版本升级 |
| Quarkus / Micronaut | 启动快、内存小，但团队不熟，统计类 SQL 的生态支持弱于 Spring 系 |

**决策**：Spring Boot 4.1.2，单体分层，按领域分包。

### 2.3 持久层

| 选项 | 代价 |
|---|---|
| **MyBatis-Plus 3.5.18** | 实体与领域对象须显式转换；无懒加载；多表靠手写 join，N+1 靠 review 发现；分页自 3.5.9 起须单独引入 `mybatis-plus-jsqlparser`，漏引只在运行期报错 |
| Spring Data JPA | 单表 CRUD 更省事，但统计 SQL 不可控，调优时看不到语句也改不动，与本项目"一半工作量在统计"直接冲突 |
| jOOQ / JdbcTemplate | SQL 完全可控，但单表 CRUD 要写大量样板，且代码生成增加构建复杂度 |

**决策**：MyBatis-Plus 3.5.18。理由：统计部分需要 SQL 摆在明面上；写入部分大量是单表 CRUD，`BaseMapper` 可省掉几乎全部样板。父 POM 以 `mybatis-plus-bom:3.5.18` 导入，starter、`mybatis-plus-jsqlparser` 三者同版本。

### 2.4 数据库

| 选项 | 代价 |
|---|---|
| **PostgreSQL 18.3** | 版本较新；托管版可选规格少于 MySQL；团队需熟悉 `timestamptz` 与部分索引 |
| MySQL 8 | 运维更普及，但窗口函数、`percentile_cont`、部分索引的表达力弱于 PG，统计要绕 |
| MariaDB | 兼容 MySQL，同样受统计表达力限制 |

**决策**：PostgreSQL 18.3。分页插件显式指定 `DbType.POSTGRE_SQL`，不依赖自动探测。

### 2.5 前端

| 选项 | 代价 |
|---|---|
| **React 18.3.1 + TypeScript 5.7.2 + Vite 6.0.5 + TanStack Query 5.62.0 + Ant Design 5.22.5** | 依赖体积较大；AntD 默认主题偏"后台"，需少量样式调整 |
| Vue 3 + Element Plus | 上手更快，但类型与契约生成的链路不如 TS 系直接 |

**决策**：React 系。类型由 `openapi-typescript` 从 `contracts/openapi.yaml` 生成，前后端字段不靠口头对齐。

### 2.6 会话与部署

| 项 | 决策 | 弃用理由 |
|---|---|---|
| 会话 | Spring Security + Spring Session JDBC，会话存库 | 不引入 Redis：为一份会话数据多一个中间件，违背"运维面尽可能窄" |
| 部署 | Docker 多阶段构建 + Compose + Caddy | 不上 K8s：单机场景下编排复杂度换不来收益 |

## 3. 决策汇总

| 层 | 选型 | 版本 |
|---|---|---|
| 语言运行时 | OpenJDK | 25.0.4.1 |
| 应用框架 | Spring Boot | 4.1.2 |
| 构建 | Maven（Wrapper 锁定） | 3.9.9 |
| 持久层 | MyBatis-Plus（`mybatis-plus-spring-boot4-starter`） | 3.5.18 |
| 持久层扩展 | `mybatis-plus-jsqlparser` | 3.5.18 |
| 数据库 | PostgreSQL | 18.3 |
| 迁移 | Flyway | 随 Boot |
| 接口契约 | springdoc-openapi → `contracts/openapi.yaml` | 随 Boot |
| 前端 | React + TypeScript + Vite + TanStack Query + Ant Design | 见 §2.5 |
| 测试 | JUnit 5 + Spring Boot Test + Testcontainers + Playwright | 随 Boot |
| 覆盖率 | JaCoCo | 0.8.12 |
| 架构约束 | ArchUnit | 1.3.x |

## 4. 版本冲突处置顺序（决策 D-10）

若 JDK 25.0.4.1 + Spring Boot 4.1.2 + MyBatis-Plus 3.5.18 的组合在实测中不可用，按以下顺序处置，不得跳步：

1. **调兼容层**：调整自动配置的排除项、启动参数、`@ConditionalOn` 相关配置，或以 `dependencyManagement` 排除冲突传递依赖。**不改三者版本。**
2. **找上游**：查 MyBatis-Plus 的补丁版本或兼容分支。**不动 JDK 与 Boot。**
3. **写 ADR**：仍不可行时，起草新 ADR（冲突现象、复现步骤、备选方案、影响面），提交评审；**审批后**方可调整版本。

**禁止项**：私自将 JDK 降至 21、Boot 降至 3.x，或未记录地降级任何一个依赖。

## 5. 后果

**好的**：

- 统计 SQL 可读可调，慢查询能直接改语句而不是改映射。
- 单表写入几乎没有样板代码，S3 的实现量集中在真正需要思考的地方。
- 单机 Compose + Caddy，运维面窄，备份与回滚都是一条命令。

**坏的（已知代价，必须接受）**：

- 实体与领域对象分离，每一处都要显式转换，代码量比 JPA 多一层。
- 没有懒加载，多表查询必须手写 join，N+1 只能靠 review 与测试发现。
- 分页需单独引入 `mybatis-plus-jsqlparser`，漏引只在运行期暴露。
- Boot 4.1.x 与 JDK 25 都较新，遇到上游 bug 时只能等待或绕行，不能靠降版本了事。

**需要后续偿还的**：

- `mybatis-plus-spring-boot4-starter` 若与 Boot 4.1.x 出现自动配置冲突，须按 §4 处置并留下记录；该风险在 S1 首次构建时验证。
- PG 18.3 的托管版规格与价格在 S5 前复核一次，必要时调整容量基线。

## 6. 与本 ADR 相关的约束

| 约束 | 位置 |
|---|---|
| 分页统一 `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`，单页上限 100 | GOV-000 §3.4 |
| 主键 UUID v7，禁用 `IdType.ASSIGN_UUID` | GOV-000 §3.4、§5.5 |
| 软删由 `@TableLogic` 统一过滤 | GOV-000 §3.4 |
| 审计字段由 `MetaObjectHandler` 填充 | GOV-000 §3.4 |
| 多表与统计 SQL 放 `resources/mapper/*.xml` | GOV-000 §3.4 |
