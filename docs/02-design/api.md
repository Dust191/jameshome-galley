---
title: 接口契约
doc_id: DES-002
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-01
updated: 2026-11-01
next_review: 2027-01-30
iter: ITER-20261101-08
related: [DES-001, DES-004, PRD-001]
---

# DES-002 接口契约

这份文档回答一个问题：**前端能调到哪些地址、拿什么参数、会收到什么错。**

本文与 `contracts/openapi.yaml` 同源：以本文为准写契约，契约由 springdoc 生成后提交，CI 比对差异，不一致即失败。

## 1. 目标与非目标

| 项 | 内容 |
|---|---|
| 本文件解决 | 资源路径、方法、参数、响应形状、错误码、权限 |
| 本文件不解决 | 表结构（`DES-001`）、指标算法（`DES-004`）、界面布局（`DES-003`） |

## 2. 通用约定

| 项 | 约定 |
|---|---|
| 前缀 | `/api/v1`；破坏性变更升 `/api/v2`，旧版保留至少 1 个 minor |
| 媒体类型 | 请求与响应均 `application/json; charset=utf-8`；导出为 `text/csv; charset=utf-8`（带 BOM） |
| 认证 | 会话存库（`Spring Session JDBC`），Cookie 名 `SESSION`，`HttpOnly` + `SameSite=Strict` + 生产环境 `Secure` |
| CSRF | `CookieCsrfTokenRepository`：服务端下发 `XSRF-TOKEN`，非 GET 请求须回传 `X-XSRF-TOKEN` 头；缺失返回 403 |
| 时间 | 请求与响应均为 ISO-8601 带偏移，如 `2026-11-01T09:30:00+08:00`；服务端存 UTC |
| 数量 | 字符串传输的小数（保留 3 位），避免 JS 浮点误差；如 `"12.500"` |
| 分页 | `?page=1&pageSize=20`；`pageSize` 上限 100，超出按 100 处理并响应 `X-Page-Size-Capped: true` |
| 排序 | `?sort=-updatedAt`，`-` 表降序；多个字段逗号分隔，白名单外的字段忽略 |
| 幂等 | `POST /movements` 支持 `Idempotency-Key`（UUID），24 小时内相同键返回首次结果并带 `Idempotent-Replay: true` |
| 追踪 | 所有响应带 `X-Trace-Id`；错误体里的 `traceId` 与之一致 |

## 3. 错误体与错误码

统一结构：

```json
{ "code": "MOVEMENT_ALREADY_REVERSED", "message": "该流水已被红冲", "field": null, "traceId": "..." }
```

| HTTP | 场景 | 错误码 |
|---|---|---|
| 400 | 参数格式错 | `VALIDATION_FAILED`（`field` 指明字段） |
| 401 | 未登录或会话过期 | `AUTH_REQUIRED`；登录失败为 `AUTH_INVALID_CREDENTIALS` |
| 403 | 权限不足、CSRF 失败、须改密 | `PERMISSION_DENIED` / `CSRF_INVALID` / `PASSWORD_CHANGE_REQUIRED` |
| 404 | 资源不存在 | `NOT_FOUND` |
| 409 | 乐观锁冲突、唯一键冲突 | `VERSION_CONFLICT` / `DUPLICATE_KEY` |
| 422 | 业务规则拒绝 | `MOVEMENT_IMMUTABLE`、`MOVEMENT_ALREADY_REVERSED`、`INVITE_CODE_INVALID`、`UNIT_IN_USE`、`CATEGORY_IN_USE`、`EXPORT_TOO_LARGE` |
| 429 | 注册或登录限流 | `RATE_LIMITED`（响应头 `Retry-After`） |
| 500 | 未预期异常 | `INTERNAL_ERROR` |

> **没有"库存不足"错误码**：`DES-001` §4.1 已裁定允许负结存，出库不因结存不足而被拒。
>
> **没有"邀请码已禁用"错误码**：D-18 要求三种无效码对外同一表现，不存在/已用/已禁用一律返回 `INVITE_CODE_INVALID`，避免被用来探测码的状态。

## 4. 资源与端点

### 4.1 认证

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` | 账号名 + 口令；连续失败 5 次锁定 15 分钟 | 匿名 |
| POST | `/auth/logout` | 销毁会话 | 已登录 |
| POST | `/auth/register` | 凭邀请码注册，角色固定 `viewer` | 匿名（限流） |
| PATCH | `/auth/password` | 改密；`must_change_password` 为真时唯一可用端点 | 已登录 |
| GET | `/auth/me` | 当前账号与权限 | 已登录 |

### 4.2 账号与邀请码

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/users` | 列表，支持 `?role=&status=` | admin |
| PATCH | `/users/{id}` | 改角色、显示名、启用/禁用 | admin |
| POST | `/users/{id}/reset-password` | 重置为临时口令，置 `must_change_password` | admin |
| GET | `/invite-codes` | 列表，支持 `?status=` | admin |
| POST | `/invite-codes` | 生成 8 位码 | admin |
| PATCH | `/invite-codes/{id}` | 仅支持 `{"status":"disabled"}` | admin |

### 4.3 主数据

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/categories` | 分类列表 | 已登录 |
| POST/PATCH | `/categories`、`/categories/{id}` | 新建 / 改名、停用 | admin、editor |
| GET | `/units` | 单位列表 | 已登录 |
| POST/PATCH | `/units`、`/units/{id}` | 新建 / 改名、停用 | admin |
| GET | `/materials` | `?keyword=&categoryId=&stockState=low\|zero\|stale&sort=` | 已登录 |
| GET | `/materials/{id}` | 详情，含当前结存 | 已登录 |
| POST | `/materials` | 建档 | admin、editor |
| PATCH | `/materials/{id}` | 局部更新 | admin、editor |
| DELETE | `/materials/{id}` | 软删；有流水则 422 | admin |

### 4.4 流水与结存

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/movements` | `?materialId=&type=&from=&to=&page=` | 已登录 |
| POST | `/movements` | 登记出入库；`type` ∈ `IN`/`OUT`/`ADJUST` | admin、editor |
| POST | `/movements/{id}/reverse` | 红冲，`note` 必填 | admin、editor |
| GET | `/balances` | 结存列表，支持 `?stockState=` | 已登录 |

登记流水的请求体：

```json
{ "materialId": "uuid", "type": "OUT", "quantity": "-5.000", "occurredAt": "2026-11-01T09:30:00+08:00", "note": "午餐领用" }
```

- `quantity` 带符号：`IN` 必须为正，`OUT` 必须为负，`ADJUST` 为差额（可正可负），`REVERSAL` 由服务端红冲接口生成，不接受客户端指定。
- 符号与 `type` 不符返回 400 `VALIDATION_FAILED`，不算业务拒绝。

### 4.5 统计（口径见 `DES-004`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/stats/overview` | 返回 M-1、M-2、M-3、M-7、M-8 五个标量 |
| GET | `/stats/category-distribution` | M-4 分类分布 |
| GET | `/stats/trend` | M-5，固定返回 30 个点 |
| GET | `/stats/top-out` | M-6，≤10 条 |
| GET | `/stats/alerts` | M-9 预警清单，分页 |

统计响应不带任何跨单位求和字段（C-5）。数量一律以字符串传输。

### 4.6 导入导出

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/exports/materials.csv` | 受当前筛选约束，不受分页限制，上限 50000 行 | 已登录 |
| GET | `/exports/movements.csv` | 同上 | 已登录 |
| POST | `/imports/materials` | `multipart/form-data`，返回逐行错误报告 | admin、editor |

## 5. 权限矩阵

| 能力 | admin | editor | viewer |
|---|---|---|---|
| 查看物料、结存、流水、统计 | ✓ | ✓ | ✓ |
| 登记出入库、红冲 | ✓ | ✓ | — |
| 建档、改物料 | ✓ | ✓ | — |
| 导入 | ✓ | ✓ | — |
| 导出 | ✓ | ✓ | ✓ |
| 单位、分类维护 | ✓ | 分类可改，单位不可 | — |
| 账号、邀请码、重置口令 | ✓ | — | — |

权限判定在服务层，不在控制器；越权统一 403 `PERMISSION_DENIED`，不区分"资源不存在"与"无权访问"，避免探测。

## 6. 待确认

| 编号 | 事项 | 说明 |
|---|---|---|
| — | 无 | 本文未产生需要裁定的设计分歧；认证方式沿用 ADR-0001 §2.6（Spring Security + Session JDBC） |

## 7. 追溯

| 本文件的结论 | 依据 |
|---|---|
| 路径、方法、状态码、错误体、分页 | GOV-000 §5.5 API 规范 |
| 会话存库、不用 JWT | ADR-0001 §2.6 |
| 无"库存不足"错误码 | DES-001 §4.1（Q-2 采纳 A） |
| 统计接口形状 | DES-004 §3 的 M-1 ~ M-9 |
| 导出上限 50000 行 | PRD US-S4 验收 3 |

## 8. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-01 | 初版建立 | ITER-20261101-08 |
| 2026-11-02 | 删除 `INVITE_CODE_DISABLED`：与 D-18「三种无效码同一表现」冲突，统一用 `INVITE_CODE_INVALID` | ITER-20261102-03 |
