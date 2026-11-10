# 贡献指南

本项目为人机协助的单人维护项目，但从第一行代码起即按团队规范执行：**所有变更走分支、留记录、可回滚**。

## 1. 环境搭建

前置工具链：

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 25 | 与 `pom.xml` 的 `<java.version>` 一致；补丁号允许跟随同大版本最新 |
| Node.js | 22 | 前端构建与脚本 |
| pnpm | 9.x | `frontend/package.json` 的 `packageManager` 已锁 9.15.9；pnpm 10 起默认拦截依赖安装脚本，会导致 `pnpm install` 失败 |
| Docker | 24+ | 数据库、对象存储与集成测试（Testcontainers） |
| Maven | 无需预装 | 由 `backend/mvnw` 提供，首次运行自动下载 |

```bash
git clone https://github.com/<owner>/<repo>.git && cd jameshome-galley
make setup     # 检查工具链、生成 .env、安装前端依赖、拉取镜像
make dev       # 起数据库并在前台运行后端
make web       # 另开终端，起前端开发服务器
```

验证：前端 `http://localhost:5173`，健康检查 `http://localhost:8080/healthz`。

逐项说明、虚拟机与端口转发等场景见 `docs/03-dev/setup.md`；测试环境要求见 `docs/03-dev/testing.md`。

## 2. 分支模型

| 分支 | 用途 | 生命周期 |
|---|---|---|
| `main` | 唯一长期分支，始终可发布 | 永久 |
| `feat/*` `fix/*` `docs/*` `chore/*` | 日常开发，从 `main` 切出 | ≤5 天，合并后删除 |
| `release/<主>.<次>` | 版本维护（1.0 后启用） | 至该 minor EOL |
| `hotfix/<版本>` | 生产紧急修复 | ≤24 小时 |

分支命名：`<类型>/<issue号或 ITER 号>-<短描述>`，例如 `feat/12-csv-import`。

## 3. 提交流程

**仓库已建立（S4 起）**：

1. 先开 issue，按 `.github/ISSUE_TEMPLATE/` 填必填字段；维护者补 `type/*` 与 `p*/*`。
2. 从 `main` 切分支，分支名带 issue 号。
3. 完成变更，按 `.github/PULL_REQUEST_TEMPLATE.md` 逐项填写，关联 issue（`Closes #<issue>`）。
4. `make lint && make test` 全绿后推送，开 PR。
5. 等 CI 与评审通过，**Squash 合并**，删除分支。
6. 合并后 24 小时内补写 `docs/07-iteration/` 迭代记录。

单人期同样走 PR：自己开、自己批，但**必须留下 PR 记录**——它是"这次改了什么、验证了什么"唯一的对外入口。

**仓库建立前（历史）**：以本地模拟流程替代，见文末附录。

## 4. 提交规范

格式：`<type>(<scope>): <subject>`，subject 用中文、≤50 字、不加句号。

type：`feat` `fix` `refactor` `perf` `docs` `test` `build` `ci` `chore` `revert`
scope 白名单：`api` `web` `db` `auth` `material` `stock` `stats` `infra` `docs` `deps` `release`

### 必需 trailer

```
feat(stock): 出库数量超过结存时拦截并提示可用量

Refs: #42
Assisted-by: DeepSeek-V4.1-Flash
Signed-off-by: Dust <3941305663@qq.com>
```

| trailer | 必填 | 说明 |
|---|---|---|
| `Signed-off-by` | 是 | DCO 签署，须与 commit author 一致 |
| `Refs` / `Closes` | 是 | 关联 issue；仓库建立前的历史提交以 ITER 编号代替，见附录 |
| `Assisted-by` | 否 | 一旦填写即须真实且在 `CONTRIBUTORS.md` 在册；无法确认则整行省略 |
| `Co-authored-by` | 否 | 仅用于人类协作者，禁止用于模型 |

提交前 `make lint` 会执行 commitlint 与署名校验，非法 message 或表外模型标识会被拦截。

## 5. 代码要求

- 格式化由 Spotless 统一，不手工调整排版。
- 模块间只能通过 `api` 包或领域事件交互；`domain` 不依赖 Spring 与 MyBatis-Plus。
- 新增依赖须满足：许可证为 Apache-2.0 / MIT / BSD / EPL；版本统一在父 `pom.xml` 的 `dependencyManagement` 声明。
- 新增查询须说明索引支撑；写操作须写审计。

## 6. 文档要求

- 改接口或表结构时，同一次提交内更新 `docs/02-design/` 对应文件。
- 新增概括性词语前，先到 `docs/00-glossary.md` 查词；未登记的先提 `proposed` 词条。
- 每轮变更后 24 小时内补写迭代记录。
- 工程规范的正文在 `docs/03-dev/spec.md`（总纲）与 `spec-work.md` / `spec-stages.md` / `spec-team.md` 三份分册；改流程或门禁前先读对应分册，改完在同轮迭代记录中登记。

## 7. 评审规则

| 项 | 规则 |
|---|---|
| 批准人数 | 至少 1 名 Maintainer；`db/migration/`、`infra/`、`security/` 相关变更需 2 名 |
| 首次响应 | ≤24 小时 |
| 合并方式 | 仅 Squash merge；禁止 merge commit；禁止 force push 到 `main` |
| 粒度 | 有效 diff ≤400 行；超过 800 行必须拆分或说明理由 |
| 不可合并 | CI 未绿、有未解决评论、分支落后 `main`、缺迭代记录、署名校验失败 |
| 关注点 | 行为是否正确、边界是否覆盖、文档是否同步、是否出现无口径的性能声明 |

评审意见分三级：`must`（不修不合）、`should`（本轮或下轮修）、`nit`（可不修）。作者对 `must` 有异议时按 `GOVERNANCE.md` §3 处理。

## 8. 贡献者协议

采用 DCO（`git commit -s`），不使用 CLA。签署身份 `Dust <3941305663@qq.com>`，须与 commit author 一致。

两类提交豁免签署检查（`scripts/check-signoff.sh` 执行）：

- **合并提交**（merge commit）不承载内容变更；
- **机器人作者**（`dependabot[bot]`、`github-actions[bot]`）无法自行签署——署名责任由 Squash 合并产生的最终提交承担，合并前须核对 CI 全绿。

## 9. 人机协作约定

本项目的改动由开发者与模型交替完成，工作区是两者共同的中间态：

- 开发者会直接修改文件，**一般不做 `add` / `commit`**；若已手动提交，会在对话开始时说明。
- 因此模型每轮动手前必须先执行 `git diff` 与 `git status`，先读懂手改内容，再决定改哪里。
- 禁止以「工作区应停留在我上次提交的状态」为前提直接覆盖；覆盖手改即视为失控。
- 提交由模型统一执行，一次提交对应一轮迭代记录，提交前须先列出发现的问题并取得开发者确认。

## 附录：仓库建立前的过渡约定（已失效）

S4 建仓之前，以本地模拟流程替代平台 PR：切分支 → 按 PR 模板自审 10 项 → `make lint && make test` → 冷静期 → Squash 合并 → 补写迭代记录；`Refs` 填 `ITER-YYYYMMDD-NN`。

该约定自仓库建立起失效，保留于此仅供追溯历史提交。
