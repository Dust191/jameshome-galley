---
title: 运行手册
doc_id: RUN-001
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-01
updated: 2026-11-01
next_review: 2027-01-30
iter: ITER-20261101-09
related: [OPS-001, RUN-002]
---

# RUN-001 运行手册

这份文档回答一个问题：**凌晨三点被叫醒，先看什么、再做什么。**

面向没睡醒的人：命令可复制，判断条件写死，不出现"视情况而定"。

## 1. 一分钟定位

```bash
docker compose -f infra/compose/compose.prod.yml ps          # 谁挂了
docker compose -f infra/compose/compose.prod.yml logs --tail=200 galley
curl -s --noproxy '*' http://127.0.0.1:8080/actuator/health/readiness
```

三种结果对应三条路：

| readiness | 含义 | 去 |
|---|---|---|
| `UP` 但页面打不开 | 代理或前端静态文件问题 | §4 |
| `DOWN` | 应用或数据库问题 | §2 或 §3 |
| 连不上 | 容器没起或端口错 | §2 |

## 2. 应用无响应

| 步 | 动作 | 预期 | 失败则 |
|---|---|---|---|
| 1 | `docker ps` 看 `galley` 状态 | `Up (healthy)` | 2 |
| 2 | `docker logs --tail=100 galley` | 有明确异常栈 | 3 |
| 3 | 看异常类型：`OutOfMemoryError` → 调 JVM 堆或重启；`Connection refused` → §3；其它 → 4 | — | — |
| 4 | 重启：`docker compose ... restart galley` | 30 秒内 healthy | 5 |
| 5 | 仍失败 → 回滚到上一镜像（`OPS-001` §5），并记事故 | — | — |

## 3. 数据库问题

| 现象 | 判据 | 处置 |
|---|---|---|
| 连接数耗尽 | 日志含 `too many clients` 或 `FATAL: remaining connection slots` | `SELECT count(*) FROM pg_stat_activity;` 找到长事务后 `pg_terminate_backend(pid)`；随后检查连接池上限与泄漏 |
| 磁盘满 | `df -h` 使用率 >90% | 先清 `pg_wal` 归档与日志；不要直接删数据文件 |
| 迁移失败 | 启动日志出现 Flyway 报错 | **不回滚库**，先停应用，按 `OPS-001` §5 回滚镜像；库结构保持，人工核对后另行处理 |
| 主从/复制 | MVP 无复制 | — |

**禁止**：在生产上直接 `DROP`/`TRUNCATE`；直接用 `psql` 改业务数据。所有数据修正必须走接口或脚本，并留 `audit_log`。

## 4. 代理与前端

| 现象 | 检查 | 处置 |
|---|---|---|
| 502 | Caddy 日志 `dial tcp ... connection refused` | 后端没起 → §2 |
| 静态资源 404 | `dist/` 是否随镜像更新 | 重新发布前端产物 |
| 证书过期 | `curl -vI https://<域名>` 看有效期 | 续期后 `docker compose restart caddy` |

## 5. 发布与回滚

发布按 `OPS-001` §4 执行；回滚按 §5。两者都必须先备份（`RUN-002` §2）。

## 6. 告警

| 告警 | 阈值 | 首响 |
|---|---|---|
| readiness 连续 3 次 DOWN | 30 秒 | §2 |
| 5xx 比例 >1% 持续 5 分钟 | — | 看日志与最近发布记录 |
| 磁盘 >85% | — | §3 |
| 备份失败 | 每日任务 | `RUN-002` §2 |

## 7. 事故记录

每次事故后必须追加一条，不写"已恢复"了事：

| 日期 | 现象 | 根因 | 处置 | 后续动作 |
|---|---|---|---|---|
| — | — | — | — | — |

## 8. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-01 | 初版建立 | ITER-20261101-09 |
