---
title: 验收记录 v0.1
doc_id: DEV-005
status: in_review
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-03
updated: 2026-11-03
next_review: 2027-01-31
iter: ITER-20261103-03
related: [GOV-000, DES-001, DES-002, DES-004, DEV-003]
---

# DEV-005 验收记录 v0.1

这份文档回答一个问题：**G3 的 12 条 UAT 各自跑没跑过、证据在哪、结论是什么。**

## 1. 环境与方法

| 项 | 内容 |
|---|---|
| 版本 | 0.1.0 |
| 日期 | 2026-11-03 |
| 后端 | Spring Boot 4.1.2，PostgreSQL 18.3（容器），MinIO |
| 前端 | React 18 + antd 5，Vite 开发服务器 |
| 自动化 | `mvn verify`：单元 + Testcontainers 集成测试，共 110 个用例；Playwright 3 条 E2E |
| 人工 | 开发者于 2026-11-02 ~ 11-03 在浏览器走查七项修复、表单输入与权限边界 |

自动化用例的类名与方法名可直接检索；人工项在「证据」列注明走查范围。

## 2. 12 条 UAT

| # | 用例 | 结果 | 证据 |
|---|---|---|---|
| 1 | 新建物料（含图片）成功，列表与详情一致 | 通过 | `EndpointCoverageTest.materials_建档后列表与详情一致`；`FileEndpointTest.upload_png返回key与url`；人工走查建档与图片回显 |
| 2 | 必填缺失 / 安全库存为负 / 数量超三位小数返回字段级错误 | 通过 | `EndpointCoverageTest.materials_字段校验返回400`（断言 `field=safetyStock`）；人工走查红字提示 |
| 3 | 入库后结存与流水累加一致 | 通过 | `BusinessFlowTest.结存恒等于流水求和_含冲正`（70.000 = 100−30−80+80） |
| 4 | 出库后结存可为负，界面按实际值显示并进入最高严重度预警 | 通过 | `BusinessFlowTest.出库超结存_允许负数并按最高严重度预警`（-15.000，severity=3，预警清单含该物料） |
| 5 | 并发登记同一物料 10 次，结存无丢失更新 | 通过 | `BusinessFlowTest.并发十次登记_结存无丢失更新`（10 线程并发，结存 100.000，流水 10 条） |
| 6 | 冲正后结存回增、原流水标记、重复冲正被拒 | 通过 | `EndpointCoverageTest.movements_冲正后结存回退且不可重复冲正`（201 → 422 `MOVEMENT_ALREADY_REVERSED`） |
| 7 | 停用物料后列表默认隐藏，历史流水与统计仍可追溯 | 通过 | `EndpointCoverageTest.停用物料默认隐藏_全部可见_流水仍可追溯`；人工走查 |
| 8 | 分类下存在物料时删除被拒绝，返回占用数量 | 通过 | `EndpointCoverageTest.categories_有物料时删除被拒`（422 `CATEGORY_IN_USE`） |
| 9 | 预警清单（低库存/零库存/呆滞）与手工核对一致 | 通过 | `EndpointCoverageTest.预警清单与列表筛选口径一致`；种子库对账 `checked=50, mismatched=0` |
| 10 | `viewer` 访问任何写接口 403，界面无编辑入口 | 通过 | `PermissionMatrixTest` 5 个用例；人工走查只读账号菜单与详情页 |
| 11 | 空数据时总览显示空态而非报错 | 通过 | `EndpointCoverageTest.空数据时总览返回零而不报错`（200，各计数为 0）；人工走查空态文案 |
| 12 | 导出 CSV 为 UTF-8 BOM，Excel 打开无乱码 | 通过 | `EndpointCoverageTest.exports_两个CSV都带BOM`；人工用 Excel 打开确认 |

## 3. 工程质量门

| 门 | 结果 | 证据 |
|---|---|---|
| 单元 + 集成覆盖率 | 通过 | 总体 84.7%，领域层 93.5%，接口层 99.4%（`jacoco:check` 绑定 `verify`） |
| 端点覆盖 | 通过 | 40 个端点逐个覆盖（`EndpointCoverageTest` 30 个 + 认证/权限/流水/对账/上传分类覆盖） |
| ArchUnit | 通过 | 4 条规则，已收回 `allowEmptyShould`；同时把 ArchUnit 升到 1.5.1（1.3.0 读不了 Java 25 字节码，此前规则实际未生效） |
| OpenAPI 零差异 | 通过 | `contracts/openapi.yaml` 由 springdoc 生成，`scripts/check-contract.sh` 与实现逐字比对 |
| 依赖漏洞 | 通过（有边界） | osv-scanner v2.7.0：0 High / 0 Critical，5 Moderate（见 §4） |
| 性能 | 通过 | 5 万物料 + 200 万流水：物料列表 P95 24ms（限 300ms），总览 P95 251ms（限 500ms） |
| 结存对账零差异 | 通过 | 种子库 50 物料、500 流水，`GET /reconcile` 返回 `mismatched=0` |
| 技术债台账 | 通过 | `DEV-004` 已建立 |

## 4. 未完成项

| 项 | 状态 | 说明 |
|---|---|---|
| Playwright 3 条 E2E | 已完成 | `frontend/e2e/flows.spec.ts` 三条主流程全绿（11.1s）；前置：dev server + `E2E_ADMIN_PASSWORD` |
| OWASP 全量扫描 | 未执行 | 开发机未能完成 NVD 数据下载；以 osv-scanner 在线查询替代（见 `DEV-003` §7），不等价于 OWASP 结果 |

## 5. 结论

12 条 UAT 全部通过；工程质量门全部达成，其中「依赖漏洞」以 osv-scanner 结果为准（0 High / 0 Critical），
OWASP dependency-check 的全量扫描未在 0.1.0 开发机完成，边界已在 `DEV-003` §7 说明。

G3 可以冻结；是否打标签发布由开发者在冻结后决定。
