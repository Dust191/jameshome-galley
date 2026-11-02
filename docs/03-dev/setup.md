---
title: 本地搭建
doc_id: DEV-001
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-01
updated: 2026-11-01
next_review: 2027-01-30
iter: ITER-20261101-09
related: [ADR-0001, ADR-0002, DEV-002]
---

# DEV-001 本地搭建

这份文档回答一个问题：**换了台机器，怎么在半小时内把它跑起来。**

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 25 LTS（本机 25.0.4.1） | 大版本必须 25；补丁号由 Toolchains 锁定，CI 允许同大版本最新补丁 |
| Maven | 3.9.9（Wrapper 自带） | 不预装，一律用 `./mvnw` |
| Node | 22.x | |
| pnpm | **9.15.9** | `frontend/package.json` 的 `packageManager` 已锁；pnpm 10+ 会拦截构建脚本 |
| Docker | 24+ | 用于 PostgreSQL 18.3 与集成测试 |
| PostgreSQL | 18.3 | 由 compose 启动，不装本机 |

## 2. 三条命令起步

```bash
git clone <repo> && cd jameshome-galley
make setup     # 前后端依赖 + 启数据库
make dev       # 起后端 8080 + 前端 5173
```

预期输出：

- `make setup` 末尾打印 `PostgreSQL 18.3 ...` 与前端依赖安装完成。
- `make dev` 后访问 `http://localhost:5173`，页面显示 `status=ok · version=0.0.1`。
- 访问 `http://localhost:8080/actuator/health` 返回 `{"status":"UP"}`。

## 3. 无 Docker 的机器：双机模式

Windows 工作区只放代码，容器与测试放虚拟机执行：

```bash
make sync      # 同步到 VM（有 rsync 用 rsync，没有退回 tar 管道）
ssh <vm> 'cd ~/jameshome-galley && make dev'
```

双向修改时以工作区为主副本，VM 只当执行环境，不在 VM 上提交。

## 3.5 对象存储（MinIO）

物料图片走对象存储，开发环境用虚拟机内的 MinIO（`ADR-0005`）：

```bash
docker run -d --name galley-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=$GALLEY_S3_ACCESS_KEY \
  -e MINIO_ROOT_PASSWORD=$GALLEY_S3_SECRET_KEY \
  -v ~/minio-data:/data minio/minio server /data --console-address ":9001"
mc alias set galley http://127.0.0.1:9000 $GALLEY_S3_ACCESS_KEY $GALLEY_S3_SECRET_KEY
mc mb --ignore-existing galley/galley-material
# 桶默认私有，图片 URL 会 403。开发环境设为匿名只读
mc anonymous set download galley/galley-material
```

预期：控制台 `http://<vm>:9001` 可登录，`mc ls galley` 列出 `galley-material/`，
且 `curl -I http://<vm>:9000/galley-material/<任意对象>` 返回 200 而非 403。
凭据写在 `.env`（已被 `.gitignore` 排除），不入仓库。

**后端在本机、MinIO 在虚拟机时**，需把对象存储端口转发过来，并把 `GALLEY_S3_ENDPOINT` 与
`GALLEY_S3_PUBLIC_BASE` 一起改成转发端口——两者必须一致，否则前端拿到的图片地址指回虚拟机内部：

```bash
ssh -N -L 19000:127.0.0.1:9000 -L 19001:127.0.0.1:9001 <user>@<vm>
export GALLEY_S3_ENDPOINT=http://127.0.0.1:19000
export GALLEY_S3_PUBLIC_BASE=http://127.0.0.1:19000/galley-material
```

## 4. 常见问题

| 现象 | 原因 | 处置 |
|---|---|---|
| `curl localhost:8080` 报 `upstream connect failed` | 本机 HTTP 代理拦截了 localhost | `curl --noproxy '*' http://127.0.0.1:8080/...` |
| `docker compose up` 后容器秒退，日志提示 pg_upgrade | PostgreSQL 18 挂载点变了 | 卷必须挂 `/var/lib/postgresql`，不是 `/var/lib/postgresql/data` |
| `pnpm install` 报 `ERR_PNPM_IGNORED_BUILDS` | pnpm 10+ 默认拦截依赖的安装脚本 | 用 pnpm 9（`corepack prepare pnpm@9.15.9 --activate`） |
| `mvnw` 报 Spotless 格式化失败 | 代码不符合 google-java-format AOSP | `./mvnw spotless:apply` |
| Docker Hub 拉取卡住 | 网络受限 | 配代理：`ssh -N -R 7891:127.0.0.1:7890 <vm>`，再给 dockerd 配 `HTTP_PROXY=http://127.0.0.1:7891` |
| 前端 5173 打不开但后端正常 | 前端未启动或端口被占 | `cd frontend && pnpm dev --host 127.0.0.1` |
| `docker pull minio/minio` 报 403 | 虚拟机默认镜像源对 MinIO 拒绝 | 改用华为镜像站同一镜像，见 `ADR-0005`；根治是清掉 `daemon.json` 里失效的 `registry-mirrors` |
| 图片 URL 返回 403 | 桶策略为私有 | `mc anonymous set download galley/galley-material` |
| 启动报 `Port 5324 was already in use` | 运行环境存在 `SERVER_PORT` 或 `GALLEY_SERVER_PORT` 变量，优先级高于 `application.yml` | 启动时显式传 `--server.port=18080`；命令行参数优先级最高 |

## 5. 验证清单

搭完后逐条确认，全绿才算环境就绪：

| 项 | 命令 | 期望 |
|---|---|---|
| 后端测试 | `cd backend && ./mvnw test` | 5 个测试通过，BUILD SUCCESS |
| 前端构建 | `cd frontend && pnpm build` | 产出 `dist/`，无类型错误 |
| 文档校验 | `node scripts/check-docs.mjs` | 全部通过 |
| 契约校验 | `bash scripts/check-contract.sh` | 通过 |
| 数据库 | `docker exec galley-db psql -U galley -c 'select 1'` | 返回 1 |

## 6. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-01 | 初版建立 | ITER-20261101-09 |
