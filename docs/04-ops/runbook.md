---
title: 运行手册
doc_id: RUN-001
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-01
updated: 2026-11-12
next_review: 2027-02-12
iter: ITER-20261112-09
related: [OPS-001, RUN-002, DEV-009]
---

# RUN-001 运行手册

这份文档回答一个问题：**凌晨三点被叫醒，先看什么、再做什么。**

面向没睡醒的人：命令可复制，判断条件写死，不出现“视情况而定”。

## 0. 先设好这一行

所有命令都在服务器 `/opt/galley` 下执行。先把它存成变量，后面每段都直接粘贴：

```bash
cd /opt/galley
C="docker compose --env-file .env.prod -f infra/compose/compose.prod.yml"
```

服务名只有五个：`api` / `web` / `caddy` / `cloudflared` / `db`。**容器名是 `<项目>-<服务>-<序号>`**（如 `galley-api-1`），脚本里一律用服务名，别用容器名硬拼。

## 1. 一分钟定位

```bash
$C ps                                              # 谁不健康
$C logs --tail=200 api                             # 应用最近日志
$C exec -T api curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

应用对外没有发布端口，`127.0.0.1:8080` 只存在于容器网络内，所以健康检查必须 `exec` 进容器打，宿主机上 curl 是会失败的——这是最容易误判为“应用挂了”的地方。

| readiness 结果 | 含义 | 去 |
|---|---|---|
| `{"status":"UP"}` 但页面打不开 | 入口或代理问题 | §5 |
| `DOWN` | 应用自身或数据库 | §2 / §4 |
| 命令超时或连不上 | 容器没起、正在重启、或卡死 | §2 |
| `exec` 报容器不存在 | 容器没起 | §2 |

## 2. 应用无响应或崩溃

| 步 | 动作 | 预期 | 失败则 |
|---|---|---|---|
| 1 | `$C ps api` | `Up (healthy)` | 2 |
| 2 | `$C logs --tail=200 api` | 有明确异常栈 | 3 |
| 3 | 看异常类型：`OutOfMemoryError` → §3；`Connection refused`/`FATAL` → §4；其它 → 4 | — | — |
| 4 | `$C restart api` | 90 秒内 `healthy`（含 start_period） | 5 |
| 5 | 仍失败 → 回滚镜像（`OPS-001` §5），并在 §9 记事故 | — | — |

## 3. 内存不足

这台机器 2 GB 内存 + 2 GB swap，五个容器共存，最容易在**构建镜像或批量导入**时被打爆。

```bash
free -h
$C stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"
dmesg -T | grep -i "killed process" | tail -5      # 有输出说明确实被 OOM 杀过
```

处置顺序：

1. 确认是哪个容器吃内存（多数是 `api`）；
2. 临时：`$C restart api`，先恢复服务；
3. 收敛：把 `JAVA_TOOL_OPTIONS` 里的堆上限或容器内存限制调低（当前堆按容器 55% 估算），改完 `$C up -d api`；
4. 根治：升配到 4 GB（`DVT-10` 登记的就是这条差额），或把 `db` 拆到独立机器。

**不要**用 `swapoff` 或调大 swap 当解决办法，swap 只能避免立即 OOM，救不了持续增长。

## 4. 数据库问题

| 现象 | 判据 | 处置 |
|---|---|---|
| 连接数耗尽 | 日志含 `too many clients` 或 `FATAL: remaining connection slots` | `$C exec -T db psql -U galley -d postgres -c "select pid,state,query from pg_stat_activity where state <> 'idle';"`，确认长事务后 `pg_terminate_backend(pid)`；随后查连接池上限 |
| 磁盘写满 | `df -h /` 使用率 >90% | 先清 `$C logs` 之外的东西：旧备份、`pg_wal` 归档、镜像；**不要直接删数据文件** |
| 迁移失败 | `api` 启动日志出现 Flyway 报错 | **不回滚库**；停 `api`，按 `OPS-001` §5 回滚镜像，库结构保持，人工核对后另行处理 |
| 库整体损坏 | `pg_isready` 失败且无法启动 | 按 `RUN-002` §4 从最近备份恢复，恢复前先把坏库另存一份 |
| 主从/复制 | 单机，无复制 | — |

**禁止**：在生产上直接 `DROP` / `TRUNCATE`；直接用 `psql` 改业务数据。所有数据修正走接口或脚本，并留 `audit_log`。

## 5. 入口失效（当前最可能发生的一类）

对外入口是 **Cloudflare Quick Tunnel**，地址形如 `https://xxx-yyy-zzz.trycloudflare.com`，**`cloudflared` 每次重启都会换一个新地址**，旧链接立即失效（`DVT-13`）。

```bash
$C ps cloudflared
$C logs cloudflared | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | tail -1
```

| 现象 | 判断 | 处置 |
|---|---|---|
| 浏览器打不开，但 `api` 是 healthy | 地址换了 | 取新地址并通知使用者 |
| 取不到地址 | `cloudflared` 没连上 Cloudflare | `$C logs --tail=100 cloudflared`，多为出站 443 被拦或 Cloudflare 侧限速；`$C restart cloudflared` |
| 返回 502 | 隧道通了但 Caddy 到不了后端 | `$C logs --tail=100 caddy`；看是不是 `api`/`web` 不健康 → §2 |
| 返回 1033 / 530 | 隧道已断但 DNS 还指着 | 同上，重启 `cloudflared` 后取新地址 |

正式入口（域名 + Named Tunnel + 备案）就绪后 `DVT-13` 才收回，届时本节整段替换。

## 6. 代理与前端

| 现象 | 检查 | 处置 |
|---|---|---|
| 页面白屏、控制台报 CSP 拦截 | 浏览器控制台看 `Content-Security-Policy` 拒绝项 | `infra/caddy/Caddyfile` 的 `Content-Security-Policy` 补对应来源，改完 `$C restart caddy` |
| 静态资源 404 | 资源是否打进了 `web` 镜像 | 重新发布前端（`OPS-001` §4）；`web` 镜像内含 `dist`，不是挂载目录 |
| 刷新子路由 404 | SPA 回退是否生效 | 检查 `frontend/nginx.conf` 的 `try_files ... /index.html` |
| TLS / 证书 | 证书由 Cloudflare 边缘持有，本机 `caddy` 只有内网 `:80` | 本机不需要续期；证书异常去 Cloudflare 侧看 |

## 7. 误删数据与恢复

| 场景 | 处置 |
|---|---|
| 误删物料（软删） | 库内 `deleted_at` 非空即可恢复，属接口能力缺口时按 `DEV-009` §5.9 处理 |
| 误删分类/单位/账号 | 无软删，只能从备份回退：按 `RUN-002` §4 恢复到独立库，核对后逐条补回，**不要整库覆盖** |
| 批量导入导错 | 立即 `$C stop api` 阻断继续写入，再按 `RUN-002` §4 恢复到事故前备份；批次号在 `audit_log` 的 `IMPORT` 记录里 |
| 整库损坏 | `RUN-002` §4 全量恢复，RTO 目标 ≤2 小时（实测空库 2 秒） |

恢复前一律先 `pg_dump` 一份现状，哪怕它是坏的——**不要在没有第二份的情况下覆盖**。

## 8. 发布与回滚

发布按 `OPS-001` §4，回滚按 `OPS-001` §5。两者都必须先备份（`RUN-002` §2）。

回滚判据只有一条：**新版本上线后 30 分钟内出现 §2 或 §4 的任一现象，且 5 分钟内无法定位**，直接回滚，不在生产上调试。

## 9. 事故记录

每次事故后必须追加一条，不写“已恢复”了事：

| 日期 | 现象 | 根因 | 处置 | 后续动作 |
|---|---|---|---|---|
| — | — | — | — | — |

## 10. 告警（未建，`T5-08`）

七条告警尚未配置，当前靠人工巡检。下表中的阈值是配置时的目标值：

| 告警 | 阈值 | 首响 |
|---|---|---|
| readiness 连续 3 次 DOWN | 30 秒 | §2 |
| 5xx 比例 >1% 且持续 5 分钟 | — | 看日志与最近发布记录 |
| P95 >1 秒且持续 10 分钟 | — | §3 / §4 |
| 磁盘使用率 >85% | — | §4 |
| 备份任务失败 | 每日 02:00 任务 | `RUN-002` §2 |
| 对账差异 ≠ 0 | 每日 02:30 任务 | §4，核对流水 |
| 隧道地址变更或断连 | — | §5 |

## 11. 八类故障对照

`DEV-009` §7.6 要求覆盖八类故障，对应关系如下：

| # | 故障类 | 本文章节 |
|---|---|---|
| 1 | 应用无响应 / 崩溃 | §2 |
| 2 | 内存不足 | §3 |
| 3 | 数据库不可用 | §4 |
| 4 | 磁盘写满 | §4 |
| 5 | 入口失效 | §5 |
| 6 | 代理 / 前端异常 | §6 |
| 7 | 数据误删或损坏 | §7 |
| 8 | 发布失败与回滚 | §8 |

## 12. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-01 | 初版建立 | ITER-20261101-09 |
| 2026-11-12 | 按真实拓扑重写：服务名取代容器名、健康检查改为容器内 exec、删除本机证书续期（TLS 由 Cloudflare 持有）、新增入口失效与内存不足两节、补齐八类故障对照 | ITER-20261112-09 |
