---
title: 备份与恢复
doc_id: RUN-002
status: in_review
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-01
updated: 2026-11-12
next_review: 2027-02-12
iter: ITER-20261112-07
related: [RUN-001, OPS-001, NFR-001]
---

# RUN-002 备份与恢复

这份文档回答一个问题：**数据没了，能恢复到哪一刻，凭什么说能恢复。**

备份与恢复已落成脚本，位于 `infra/backup/`，部署路径为 `/opt/galley/infra/backup/`。本文描述脚本的实际行为，不是示意图。

## 1. 备份什么

| 对象 | 方式 | 频率 | 保留 |
|---|---|---|---|
| 数据库（ECS 内容器自建） | `pg_dump -Fc` 全量，`backup-db.sh` | 每日 02:00（cron） | 本地 7 日 / 4 周 / 12 月；OSS 全量留存 |
| 对象存储图片 | OSS 版本控制 | 实时 | 跟随 OSS 策略 |
| 应用配置 | 环境变量清单（不含口令） | 变更时 | 随仓库 |
| 镜像 | 每次发布打标签 | 发布时 | 保留最近 10 个 |

**口令不进备份**：`pg_dump` 不含数据库账号口令；`.env.prod` 不入仓、不入备份包，由部署环境独立托管。

## 2. 每日备份

由部署账号的 crontab 触发（`crontab -l` 可见）：

```
0 2 * * * /opt/galley/infra/backup/backup-db.sh >> /opt/galley/backup/cron.log 2>&1
```

脚本每一步都可单独复现，手工执行等价：

```bash
/opt/galley/infra/backup/backup-db.sh            # 完整备份
/opt/galley/infra/backup/backup-db.sh --prune-only  # 只按留存规则清理本地旧文件
```

| 步 | 动作 | 判据 |
|---|---|---|
| 1 | 导出 | `compose exec -T db pg_dump -Fc` 写本地，文件非空且头 5 字节为 `PGDMP` |
| 2 | 校验 | 生成 `.sha256`；`head -c 5` 与大小双重判定 |
| 3 | 上传 | 内网 endpoint `oss-cn-hangzhou-internal.aliyuncs.com`，路径 `backup/db/YYYY/MM/` |
| 4 | 回查 | `ossutil stat` 比对远端 `Content-Length` 与本地大小；单段上传的 ETag 即内容 MD5，一并比对 |
| 5 | 留存 | 本地按日 7 / 周 4 / 月 12 取并集，其余连同 `.sha256` 删除 |
| 6 | 记账 | 追加 `/opt/galley/backup/backup.log`，含时间、大小、耗时、sha256 前 16 位 |

第 4 步不可省：**上传成功不等于对象完整**，回查才认。任一步失败脚本以非 0 退出，cron 会在 `cron.log` 留痕。

首次部署需安装 `ossutil`（本机无该命令时脚本直接报错，不会静默跳过上传）：

```bash
cd /tmp && V=2.2.1
curl -s -o o.zip "https://gosspublic.alicdn.com/ossutil/v2/$V/ossutil-$V-linux-amd64.zip"
python3 -c "import zipfile;zipfile.ZipFile('o.zip').extractall('.')"
install -m 0755 "ossutil-$V-linux-amd64/ossutil" /usr/local/bin/ossutil
```

## 3. 恢复目标

| 指标 | 目标 | 实况 |
|---|---|---|
| RPO（能容忍丢多少） | ≤24 小时 | 日备份；规范 §7.4 要求 ≤5 分钟，差额按 `DVT-11` 登记，收窄需开 WAL 归档 |
| RTO（多久恢复） | ≤2 小时 | 空库实测 2 秒（含下载、建库、恢复、核对、清理）；真实数据量下需重测 |

## 4. 恢复步骤

`restore-db.sh` 默认恢复到**独立的演练库**，核对后自动删除，不碰生产库。覆盖生产库必须显式加 `--confirm-production`，这是防手滑的硬闸。

```bash
# 演练（默认）：恢复到 galley_restore_check，核对完自动清理
/opt/galley/infra/backup/restore-db.sh oss://jameshome-galley/backup/db/2026/11/galley-20261112-211245.dump

# 演练并保留结果库，便于人工进去看
/opt/galley/infra/backup/restore-db.sh <dump> --keep

# 真实恢复：先停应用，再显式确认覆盖生产库
docker compose -f infra/compose/compose.prod.yml stop api
/opt/galley/infra/backup/restore-db.sh <dump> --target galley --confirm-production
docker compose -f infra/compose/compose.prod.yml start api
```

脚本自带的安全约束：拒绝以 `postgres` / `template1` 为目标；目标库等于生产库时必须加 `--confirm-production`；恢复前核对 `.sha256`，不匹配直接中止。

真实恢复是不可逆操作，执行前仍需人工确认三件事：当前库确实不可用（不是应用问题）、已取得开发者同意、已把坏库另存一份（`pg_dump` 一份即可，别再叠加破坏）。

恢复后除脚本输出的行数外，还应跑一次结存恒等式（`DES-001` §4.1）：

```bash
docker compose -f infra/compose/compose.prod.yml exec -T db psql -U galley -d galley -c "
  SELECT count(*) FROM (
    SELECT b.material_id
    FROM stock_balance b
    JOIN (SELECT material_id, SUM(quantity) q FROM stock_movement GROUP BY material_id) m
      ON m.material_id = b.material_id
    WHERE b.quantity <> m.q
  ) t;"
```

返回 `0` 才算恢复成功；非 0 说明备份本身有问题，改从前一天的备份重试。

## 5. 演练

| 项 | 要求 |
|---|---|
| 频率 | 每季度一次；重大变更（升版本、改表结构）后追加一次 |
| 方式 | 从 OSS 取备份，恢复到独立库，不碰 prod |
| 判据 | 迁移版本一致、关键表行数一致、`app_user` 与 `flyway_schema_history` 的行级 MD5 一致 |
| 记录 | 填入下方记录表 |

演练记录（每次追加一行，`--keep` 跑完后记得删掉结果库）：
| 日期 | 备份文件 | 端到端耗时 | 结果 | 遗留 |
|---|---|---|---|---|
| 2026-11-12 | `oss://jameshome-galley/backup/db/2026/11/galley-20261112-211245.dump` | 2 s | 通过：sha256 一致；`flyway_max_version=3`；11 张表；`app_user` 行级 MD5 `e792ad9c…` 与生产一致；迁移历史 MD5 `9d20681a…` 一致 | 库近空，真实数据量下需重测 RTO |

## 6. 裁定记录

| 编号 | 事项 | 裁定 | 日期 |
|---|---|---|---|
| Q-1 | 保留策略 7 日 / 4 周 / 12 月是否够用 | **够用**，按现值执行，真实数据落库后按实际容量复核一次 | 2026-11-01 |
| Q-2 | 是否启用 WAL 归档把 RPO 压到分钟级 | 暂不启用。当前 RPO 24 小时可接受 | 2026-11-01 |
| Q-3 | 自建 PostgreSQL 的 RPO 与规范 ≤5 分钟的差额 | 接受 24 小时，按 `DVT-11` 登记；启用 WAL 归档后再收窄 | 2026-11-12 |
| Q-4 | 备份文件要不要在 OSS 侧再设过期规则 | 暂不设。备份总量小，先全量留存；容量或账单有感时由 `T5-05` 的桶策略统一处理 | 2026-11-12 |

## 7. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-01 | 初版建立 | ITER-20261101-09 |
| 2026-11-12 | 按自建数据库与内网 OSS 重写备份与恢复命令：服务名改为 `db` / `api`，上传走内网 endpoint，RPO 差额登记 `DVT-11` | ITER-20261112-01 |
| 2026-11-12 | 备份与恢复落成脚本：`backup-db.sh`（导出、校验、上传、回查、留存）与 `restore-db.sh`（演练库、安全闸、行级核对）；补首次演练记录与实测 RTO | ITER-20261112-07 |
