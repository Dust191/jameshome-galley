---
title: 本地搭建
doc_id: DEV-001
status: in_review
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

## 4. 常见问题

| 现象 | 原因 | 处置 |
|---|---|---|
| 启动报 `Port 12888 was already in use` | 宿主环境变量 `GALLEY_SERVER_PORT` 覆盖了默认端口 | `env -u GALLEY_SERVER_PORT java -jar ...`，或改 `.env` |
| `curl localhost:8080` 报 `upstream connect failed` | 本机 HTTP 代理拦截了 localhost | `curl --noproxy '*' http://127.0.0.1:8080/...` |
| `docker compose up` 后容器秒退，日志提示 pg_upgrade | PostgreSQL 18 挂载点变了 | 卷必须挂 `/var/lib/postgresql`，不是 `/var/lib/postgresql/data` |
| `pnpm install` 报 `ERR_PNPM_IGNORED_BUILDS` | pnpm 10+ 默认拦截依赖的安装脚本 | 用 pnpm 9（`corepack prepare pnpm@9.15.9 --activate`） |
| `mvnw` 报 Spotless 格式化失败 | 代码不符合 google-java-format AOSP | `./mvnw spotless:apply` |
| Docker Hub 拉取卡住 | 网络受限 | 配代理：`ssh -N -R 7891:127.0.0.1:7890 <vm>`，再给 dockerd 配 `HTTP_PROXY=http://127.0.0.1:7891` |
| 前端 5173 打不开但后端正常 | 前端未启动或端口被占 | `cd frontend && pnpm dev --host 127.0.0.1` |

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
