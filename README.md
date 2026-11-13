# jameshome-galley

面向**团体厨房**的**库存远程查看**系统。让不在厨房现场的人——负责人、采购、管理者——随时看到「现在有什么、还剩多少、哪些快没了」。

它只做四件事：**记录、修正、统计、查看**。不是仓储管理系统，也不是采购系统。

## 三条硬边界

| 边界 | 含义 |
|---|---|
| **非商业** | 不产生交易，不接入支付、结算、采购付款，不与任何资金流打通 |
| **不改写现场作业** | 只做记录与查看；不控制硬件（电子秤、温湿度、门禁），不自动生成采购单，不替代现场作业系统 |
| **不做组织集成** | 不绑定学校、企业或机构的身份体系，不接入 OA / HR / 统一认证，账号自建 |

三条边界写入 `docs/01-product/PRD-001.md` 与 `SCOPE-001`，任何迭代不得突破。

## 当前状态

**0.2.0 已发布并上线**（2026-11-12），运行在阿里云杭州的单台 ECS 上，处于 **S5 部署上线的执行段**：首次部署已完成，备份与恢复演练已通过，G5 尚未验收。

对外地址由 **Cloudflare Quick Tunnel** 承载，形态是 `https://<随机名>.trycloudflare.com`。这是临时入口：**服务重启即换址，无 SLA**，正式域名与备案不在上线验收范围内（见 `DVT-13`、`DEV-009` §7.8）。

| 维度 | 现状 |
|---|---|
| 版本 | 0.2.0（`backend/pom.xml` 与 `frontend/package.json` 同版本） |
| 阶段 | S0 ~ S4 已关账，S5 执行段进行中，S6 未进入 |
| 数据库 | Flyway `V1` ~ `V3`，8 张领域表 + 2 张会话表 |
| 对外契约 | `contracts/openapi.yaml` 32 条路径 / 41 个操作，与运行实例逐字比对 |
| 自动化测试 | 109 个 `@Test` 方法（2026-11-13 实测），含端点覆盖、权限矩阵、并发与幂等 |
| 上线形态 | 五服务容器编排：`api` / `web` / `caddy` / `cloudflared` / `db` |

## 能力清单

### 账号与权限

三种角色，权限按接口逐条校验（`PermissionMatrixTest` 覆盖）：

| 角色 | 能做什么 |
|---|---|
| 管理员 `admin` | 全部读写、账号管理、邀请码、分类与单位字典、数据导出、对账查询 |
| 记录员 `editor` | 物料建档与编辑、出入库与盘点登记、红冲、查看统计 |
| 查看者 `viewer` | 只读：总览、物料详情、统计报表；任何写接口返回 403 |

注册需邀请码；账号自建，不接外部身份体系。首次登录强制改密，管理员重置密码后同样拦住。登录走会话（Spring Session JDBC，会话存库），非 GET 请求须带 `X-XSRF-TOKEN`。

### 物料

物料按「分类 + 规格 + 单位」建模，新增一类物料不改表、不改接口。支持图片上传（存对象存储，私有桶 + 签名地址下发，默认 1 小时有效）、软删、状态机（启用 / 停用 / 已归档）、乐观锁并发。

### 流水与结存

流水分四类：`IN` 入库、`OUT` 出库、`ADJUST` 盘点调整、`REVERSAL` 红冲。

- **只增不改**：错误流水不得编辑或删除，只能发起红冲——生成方向相反的一条 `REVERSAL`，原流水标记 `reversed`。数据库触发器拒绝删除，并保证红冲方向与原流水相反。
- **结存原子更新**：`INSERT ... ON CONFLICT DO UPDATE`，不做「先读再写」；登记流水与更新结存在同一事务内。
- **幂等**：登记请求可带 `idempotencyKey`（≤64 字符），同键重复提交返回首次结果；未传键不做幂等。
- **允许负结存**：出库超结存不拦截，按实际值显示并进入最高严重度预警。

### 统计

九项指标，口径写在 `docs/02-design/metrics.md`，全部由流水与字典聚合，不存中间表：总览、分类分布、出库 Top、30 天趋势、预警清单、呆滞物料等。列表与统计分页上限 100，超出按 100 截断并置 `capped`。

### 导入导出

- 物料批量导入 CSV：整批原子，失败逐行给错误报告，可下载。
- 导出物料与流水 CSV：UTF-8 BOM，Excel 直接可读。

### 对账与审计

- **结存对账**：每日 02:30 按全量流水重算结存并与 `stock_balance` 比对，差异非零即记日志；另有 `GET /api/v1/reconcile` 供人工与测试查询。
- **审计日志**：全部写路径在同一事务内写入 `audit_log`（jsonb 快照，账号快照手工脱敏，口令字段不入审计）。

## 技术基线

| 层 | 选型 | 版本 |
|---|---|---|
| 语言运行时 | OpenJDK | 25 |
| 应用框架 | Spring Boot | 4.1.2 |
| 构建 | Maven Wrapper | 3.9.16 |
| 持久层 | MyBatis-Plus | 3.5.18.1 |
| 数据库 | PostgreSQL | 18.3 |
| 迁移 | Flyway | 随 Boot |
| 认证会话 | Spring Security + Spring Session JDBC | 随 Boot |
| 契约 | springdoc-openapi | 3.2.0 |
| 对象存储 | MinIO 客户端（开发 MinIO，生产 OSS） | 8.6.0 |
| 前端 | React + TypeScript + Vite | 18.3.1 / 5.9.3 / 6.4.5 |
| 前端组件 | Ant Design + TanStack Query | 5.29.3 / 5.62.16 |
| 包管理 | pnpm | 9.15.9（锁定，pnpm 10 会拦截安装脚本） |
| 测试 | JUnit 5 + Testcontainers + Playwright | 1.21.4 / 1.64.1 |

统计类查询是工作量重心，因此选 MyBatis 而非 ORM：SQL 摆在明面上，窗口函数与多表聚合可直接调。代价是实体与领域对象需显式转换，多表查询靠手写 join。

## 仓库结构

```
backend/     Spring Boot 单体，按领域纵切，模块内四层（api / application / domain / infrastructure）
  ├─ modules/identity     账号、邀请码、认证
  ├─ modules/material     物料、分类、单位
  ├─ modules/stock        流水、结存、红冲
  ├─ modules/stats        九项指标
  ├─ modules/file         对象存储
  ├─ modules/imports      物料批量导入
  └─ modules/export       CSV 导出
frontend/    React + TypeScript + Vite，按 features 分目录
contracts/   OpenAPI 契约固化文件，由后端生成后提交
infra/       生产与开发编排、Caddy 配置、备份与恢复脚本
docs/        产品、设计、开发、运维、治理、迭代记录
scripts/     校验脚本（环境变量、契约、提交信息、署名、文档结构）
```

模块边界由 ArchUnit 强制四条规则，空集即失败：`domain` 不依赖框架、`common` 不依赖 `modules`、`api` 不直达 `infrastructure`、Controller 不暴露 `DO`。

## 本地运行

前置：JDK 25、Docker 24+、Node 22 与 pnpm 9。Maven 由 Wrapper 提供，无需预装。

```bash
git clone git@github.com:Dust191/jameshome-galley.git && cd jameshome-galley
make setup    # 检查工具链、生成 .env、安装前端依赖、拉取镜像
make dev      # 起数据库并在前台运行后端
make web      # 另开终端，起前端开发服务器
```

验证：

- 前端 <http://localhost:5173>
- 健康检查 <http://localhost:8080/healthz> → `{"status":"ok","version":"0.2.0"}`

管理员账号的初始口令由启动时生成并打印在启动日志里（开发环境每次重启重新生成）；登录后强制改密。开发环境的种子数据由 `scripts/gen-seed.py` 以固定随机种子生成，8 分类 / 6 单位 / 50 物料 / 3 账号 / 500 流水。

| 命令 | 作用 |
|---|---|
| `make test` | 后端测试 + 前端类型检查 |
| `make lint` | Spotless、ESLint、Prettier、文档结构校验 |
| `make build` | 后端打包 + 前端构建 |
| `make up` / `make down` | 全容器化启动 / 停止 |
| `make sync` | 同步工作区到虚拟机（本机不便跑容器时的备用路径） |

## 工程约束

这个仓库从第一行代码起按团队规范执行，单人期也不放宽：

- **质量门**：行覆盖率总体 ≥70%、领域层 ≥85%；41 个契约操作逐个覆盖；3 条 E2E 主流程（建档入库、冲正回退、导入错误报告）；性能门槛为列表 P95 ≤300ms、总览 P95 ≤500ms（0.1.0 实测 24ms / 251ms，5 万物料 + 200 万流水）。
- **CI**：三个 job——后端 `mvnw verify`、前端 lint + 类型 + 构建、guards（契约、环境变量、编排 `config -q`、提交信息、署名、文档结构）。
- **提交**：`<type>(<scope>): <subject>`，scope 取白名单十一项；必须带 `Refs` 与 `Signed-off-by`，`Assisted-by` 选填但填了就要真实且在册。
- **文档**：每份文档有 front-matter 十字段与唯一编号；用词以 `docs/00-glossary.md` 为准；每轮变更留一份迭代记录，审定前不进入下一轮。

## 部署形态

单机 Docker 部署，五服务同机：`api`（JVM）、`web`（nginx + 静态资源）、`caddy`（内网反向代理）、`cloudflared`（出站隧道）、`db`（PostgreSQL 18 容器）。

已知取舍，均已登记在 `docs/03-dev/deviations.md`：

| 项 | 现状 | 代价 |
|---|---|---|
| 计算规格 | 2 vCPU / 1740 MB / 40 GB，另配 2 GB swap（`DVT-10`） | 低于规范要求，JVM 堆需下调，磁盘需盯占用 |
| 数据库 | 容器自建，非托管（`DVT-11`） | 每日 02:00 `pg_dump` 上传 OSS，RPO 24 小时；无高可用 |
| 入口 | Quick Tunnel（`DVT-13`） | 重启换址、无 SLA；正式域名与备案为可选拓展，不在上线验收内 |

备份与恢复脚本在 `infra/backup/`：导出后回查大小与 MD5，本地按日 / 周 / 月留存；恢复默认落独立演练库并做行级核对，覆盖生产库需显式加 `--confirm-production`。

## 文档导航

| 想了解 | 从这里读 |
|---|---|
| 全项目有什么文档 | `docs/00-index.md` |
| 产品需求与范围 | `docs/01-product/PRD-001.md`、`SCOPE-001` |
| 领域模型与接口契约 | `docs/02-design/domain-model.md`、`api.md` |
| 统计口径 | `docs/02-design/metrics.md` |
| 本地搭建与编码约定 | `docs/03-dev/setup.md`、`conventions.md` |
| 工程规范 | `docs/03-dev/spec.md`（总纲）与 `spec-work.md` / `spec-stages.md` |
| 部署与运行手册 | `docs/04-ops/deploy.md`、`runbook.md`、`backup-restore.md` |
| 已知偏离与技术债 | `docs/03-dev/deviations.md`、`tech-debt.md` |
| 每一轮做了什么 | `docs/07-iteration/` |

## 参与

提交前请读 `CONTRIBUTING.md`。工程规范从 `docs/03-dev/spec.md` 读起，作业与阶段细则见 `DEV-008`、`DEV-009`。对外署名统一为 `The JamesHome Contributors`，个人身份只出现在 Git 提交元数据与 `CONTRIBUTORS.md`。

## 许可证

[Apache License 2.0](LICENSE)。第三方依赖许可清单见 `docs/05-governance/third-party-licenses.md`。

Maintained by The JamesHome Contributors. See [CONTRIBUTORS.md](CONTRIBUTORS.md).
