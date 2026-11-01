# 贡献指南

本项目为人机协助的单人维护项目，但从第一行代码起即按团队规范执行：**所有变更走分支、留记录、可回滚**。

## 1. 分支模型

| 分支 | 用途 | 生命周期 |
|---|---|---|
| `main` | 唯一长期分支，始终可发布 | 永久 |
| `feat/*` `fix/*` `docs/*` `chore/*` | 日常开发，从 `main` 切出 | ≤5 天，合并后删除 |
| `release/<主>.<次>` | 版本维护（1.0 后启用） | 至该 minor EOL |
| `hotfix/<版本>` | 生产紧急修复 | ≤24 小时 |

分支命名：`<类型>/<issue号或 ITER 号>-<短描述>`，例如 `feat/12-csv-import`。

## 2. 本地模拟 PR 流程

GitHub 仓库在 G3 通过后创建。在此之前，以本地模拟流程替代平台 PR：

1. 从 `main` 切分支。
2. 完成变更，按 `.github/PULL_REQUEST_TEMPLATE.md` 逐项填写**自审清单 10 项**，写入 PR 描述草案（提交到 `docs/07-iteration/` 对应 ITER 的"变更过程"）。
3. 运行 `make lint && make test`，全绿。
4. 冷静期：完成自审后至少间隔一段时间再合并，具体时长见 `DEV-002`。
5. Squash 合并到 `main`，删除分支，补写 ITER 记录。

单人期不得以"就我一个人"为由跳过上述任何一步。

## 3. 提交规范

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
| `Refs` / `Closes` | 是 | 关联 issue；仓库建立前以 ITER 编号代替，见 §6 |
| `Assisted-by` | 否 | 一旦填写即须真实且在 `CONTRIBUTORS.md` 在册；无法确认则整行省略 |
| `Co-authored-by` | 否 | 仅用于人类协作者，禁止用于模型 |

提交前 `make lint` 会执行 commitlint 与署名校验，非法 message 或表外模型标识会被拦截。

## 4. 代码要求

- 格式化由 Spotless 统一，不手工调整排版。
- 模块间只能通过 `api` 包或领域事件交互；`domain` 不依赖 Spring 与 MyBatis-Plus。
- 新增依赖须满足：许可证为 Apache-2.0 / MIT / BSD / EPL；版本统一在父 `pom.xml` 的 `dependencyManagement` 声明。
- 新增查询须说明索引支撑；写操作须写审计。

## 5. 文档要求

- 改接口或表结构时，同一次提交内更新 `docs/02-design/` 对应文件。
- 新增概括性词语前，先到 `docs/00-glossary.md` 查词；未登记的先提 `proposed` 词条。
- 每轮变更后 24 小时内补写迭代记录。

## 6. 仓库建立前的过渡约定

GitHub 仓库在 G3 通过后创建。在此之前：

- `Refs` 填对应的 `ITER-YYYYMMDD-NN`，建仓后改为 `Refs: #<issue>` 并在 ITER 中注明映射。
- 分支保护由本地规则替代：`main` 不接受直接提交，所有变更经分支 + 自审 + squash 合并。
- 该过渡随仓库建立自动失效，不得沿用。

## 7. 贡献者协议

采用 DCO（`git commit -s`），不使用 CLA。签署身份 `Dust <3941305663@qq.com>`，须与 commit author 一致。

## 8. 人机协作约定

本项目的改动由开发者与模型交替完成，工作区是两者共同的中间态：

- 开发者会直接修改文件，**一般不做 `add` / `commit`**；若已手动提交，会在对话开始时说明。
- 因此模型每轮动手前必须先执行 `git diff` 与 `git status`，先读懂手改内容，再决定改哪里。
- 禁止以「工作区应停留在我上次提交的状态」为前提直接覆盖；覆盖手改即视为失控。
- 提交由模型统一执行，一次提交对应一轮迭代记录，提交前须先列出发现的问题并取得开发者确认。
