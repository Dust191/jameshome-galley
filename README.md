# jameshome-galley

面向**团体厨房**的**库存远程查看**系统：让不在厨房现场的人随时看到「现在有什么、还剩多少、哪些快没了」。

本项目只做三件事——**记录、修正、统计与远程查看**。它不是仓储管理系统，也不是采购系统。

## 三条硬边界

1. **非商业**：不产生交易，不接入支付、结算、采购付款，不与任何资金流打通。
2. **不改写现场作业**：只做记录与查看；不控制硬件（电子秤、温湿度、门禁），不自动生成采购单，不替代现场作业系统。
3. **不做组织集成**：不绑定学校、企业或机构的身份体系，不接入 OA / HR / 统一认证，账号自建。

以上三条写入 `docs/01-product/PRD-001.md`，任何迭代不得突破。

## 快速开始

前置：JDK 25、Docker、Node 22 与 pnpm。Maven 由 Wrapper 提供，无需预装。

```bash
git clone <repo-url> && cd jameshome-galley
make setup          # 检查工具链、生成 .env、安装前端依赖、拉取镜像
make dev            # 起数据库并在前台运行后端
make web            # 另开一个终端，起前端开发服务器
```

验证：

- 前端 <http://localhost:5173>
- 健康检查 <http://localhost:8080/healthz> → `{"status":"ok","version":"0.0.1"}`

更多环境说明见 `docs/03-dev/setup.md`（S2 建立）。容器相关操作若在本机不可用，可先 `make sync` 同步到 Ubuntu 虚拟机再执行，细则见 `DEV-001`。

## 常用命令

| 命令 | 作用 |
|---|---|
| `make setup` | 一次性准备：工具链检查、`.env`、前端依赖、镜像 |
| `make dev` | 起数据库 + 前台运行后端 |
| `make web` | 起前端开发服务器 |
| `make test` | 后端测试 + 前端类型检查 |
| `make lint` | Spotless、ESLint、Prettier 检查 |
| `make build` | 后端打包 + 前端构建 |
| `make up` / `make down` | 全容器化启动 / 停止 |
| `make sync` | 同步工作区到虚拟机（容器操作前置） |

## 目录

```
backend/     Spring Boot 单体，按领域纵切（material / stock / stats / identity）
frontend/    React + TypeScript + Vite
contracts/   OpenAPI 契约固化文件，由后端生成后提交
infra/       Compose 编排、Caddy 配置、备份脚本
docs/        产品、设计、开发、运维、治理、迭代记录
scripts/     校验脚本（环境变量一致性、提交署名）
```

模块边界由 ArchUnit 强制：模块间只能通过 `api` 包或领域事件交互，`domain` 不依赖框架，Controller 不得返回 DO。

## 参与

提交前请阅读 `CONTRIBUTING.md`。提交信息遵循 Conventional Commits 子集，并携带 `Refs` 与 `Signed-off-by` 两个必需 trailer；`Assisted-by` 为选填，填写即须真实且在册。

## 许可证

[Apache License 2.0](LICENSE)。第三方依赖许可清单见 `docs/05-governance/third-party-licenses.md`（S4 建立）。

Maintained by The JamesHome Contributors. See [CONTRIBUTORS.md](CONTRIBUTORS.md).
