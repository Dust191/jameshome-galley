---
title: 备份与恢复
doc_id: RUN-002
status: in_review
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-01
updated: 2026-11-12
next_review: 2027-02-12
iter: ITER-20261112-01
related: [RUN-001, OPS-001, NFR-001]
---

# RUN-002 备份与恢复

这份文档回答一个问题：**数据没了，能恢复到哪一刻，凭什么说能恢复。**

## 1. 备份什么

| 对象 | 方式 | 频率 | 保留 |
|---|---|---|---|
| 数据库（ECS 内容器自建） | `pg_dump -Fc` 全量 | 每日 02:00 | 7 日 / 4 周 / 12 月 |
| 对象存储图片 | OSS 版本控制（bucket 未建；跨区复制暂不做） | 实时 | 跟随 OSS 策略 |
| 应用配置 | 环境变量清单（不含口令） | 变更时 | 随仓库 |
| 镜像 | 每次发布打标签 | 发布时 | 保留最近 10 个 |

**口令不进备份**：`pg_dump` 不含数据库账号口令；`.env` 不入仓、不入备份包，由部署环境独立托管。

保留策略为初始值，**S5 上线前按实际数据量复核**（见 §6 待确认）。

## 2. 每日备份

```bash
# 服务名与 compose.prod.yml 保持一致：db
docker compose -f infra/compose/compose.prod.yml exec -T db \
  pg_dump -U galley -Fc galley > /backup/galley-$(date +%F).dump
```

上传走 OSS 内网 endpoint，不绕公网、不消耗公网流量：

```bash
ossutil cp /backup/galley-$(date +%F).dump \
  oss://<bucket>/backup/db/$(date +%Y/%m)/ \
  -e oss-cn-hangzhou-internal.aliyuncs.com
```

| 步 | 动作 | 预期 |
|---|---|---|
| 1 | 导出 | 文件大小 >0 且与昨日同量级 |
| 2 | 校验 | `pg_restore -l <file> >/dev/null` 退出码 0 |
| 3 | 上传 | OSS `backup/db/` 前缀，按日期分目录；用内网 endpoint `oss-cn-hangzhou-internal.aliyuncs.com` |
| 4 | 记账 | 写入备份日志表：日期、字节数、校验结果 |

第 2 步不可省。**未校验的备份等于没有备份**。

## 3. 恢复目标

| 指标 | 目标 | 说明 |
|---|---|---|
| RPO（能容忍丢多少） | ≤24 小时 | 自建库只有日备份；规范 §7.4 要求 ≤5 分钟，差额按 `DVT-11` 登记。收窄需开 WAL 归档并另存归档日志 |
| RTO（多久恢复） | ≤2 小时 | 单机恢复，含拉镜像与校验 |

## 4. 恢复步骤

恢复是**不可逆操作**，执行前必须：

1. 确认当前库确实不可用（不是应用问题）；
2. 已取得开发者同意；
3. 已把当前库另存一份（哪怕它坏了）。

```bash
# 1. 停应用，避免恢复期间写入
docker compose -f infra/compose/compose.prod.yml stop api

# 2. 重建空库
docker compose -f infra/compose/compose.prod.yml exec -T db psql -U galley -c 'DROP DATABASE IF EXISTS galley;'
docker compose -f infra/compose/compose.prod.yml exec -T db psql -U galley -c 'CREATE DATABASE galley;'

# 3. 恢复
docker compose -f infra/compose/compose.prod.yml exec -T db \
  pg_restore -U galley -d galley --clean --if-exists < /backup/galley-2026-11-01.dump

# 4. 校验：结存恒等式（DES-001 §4.1）
docker compose -f infra/compose/compose.prod.yml exec -T db psql -U galley -d galley -c "
  SELECT count(*) FROM (
    SELECT b.material_id
    FROM stock_balance b
    JOIN (SELECT material_id, SUM(quantity) q FROM stock_movement GROUP BY material_id) m
      ON m.material_id = b.material_id
    WHERE b.quantity <> m.q
  ) t;"

# 5. 起应用
docker compose -f infra/compose/compose.prod.yml start api
```

第 4 步返回 `0` 才算恢复成功；非 0 说明备份本身有问题，改从前一天的备份重试。

## 5. 演练

| 项 | 要求 |
|---|---|
| 频率 | 每季度一次 |
| 方式 | 在 staging 上用真实备份文件恢复，不碰 prod |
| 记录 | 填入下表，含耗时与发现的问题 |
| 判据 | 恢复后 `DES-001` §4.1 恒等式校验为 0，且随机抽 3 个物料数量与备份前一致 |

演练记录：

| 日期 | 备份文件 | 耗时 | 结果 | 遗留 |
|---|---|---|---|---|
| — | — | — | — | — |

## 6. 裁定记录

| 编号 | 事项 | 裁定 | 日期 |
|---|---|---|---|
| Q-1 | 保留策略 7 日 / 4 周 / 12 月是否够用 | **够用**，按现值执行，S5 上线前按实际数据量复核一次 | 2026-11-01 |
| Q-2 | 是否启用 WAL 归档把 RPO 压到分钟级 | 暂不启用。当前 RPO 24 小时可接受；服务器采购后再评估 | 2026-11-01 |
| Q-3 | 自建 PostgreSQL 的 RPO 与规范 ≤5 分钟的差额 | 接受 24 小时，按 `DVT-11` 登记；启用 WAL 归档后再收窄 | 2026-11-12 |

> **S5 准备期版本**：服务器与 OSS 已就位，但 bucket、备份脚本与恢复演练尚未落地；在 L-01 与 L-06 闭环前，本文不具备可执行性。

## 7. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-01 | 初版建立 | ITER-20261101-09 |
| 2026-11-12 | 按自建数据库与内网 OSS 重写备份与恢复命令：服务名改为 `db` / `api`，上传走内网 endpoint，RPO 差额登记 `DVT-11` | ITER-20261112-01 |
