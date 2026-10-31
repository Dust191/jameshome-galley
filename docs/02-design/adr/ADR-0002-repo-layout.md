---
title: 仓库目录结构与构建入口
doc_id: ADR-0002
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-10-31
updated: 2026-10-31
next_review: 2027-01-29
iter: ITER-20261031-02
related: [ADR-0001, ADR-0003]
---

# ADR-0002 采用 GOV-000 §3.3 骨架，并补入 Maven Wrapper

这份文档回答一个问题：**仓库长什么样，构建入口放在哪，为什么不再多加一层模块。**

- **状态**：accepted（依据 GOV-000 §3.3 与决策 D-09）

## 1. 背景

GOV-000 §3.3 给出了强制目录骨架，但其中有两处需要在开工前定死：一是 `.mvn/{maven.config,jvm.config}` 已列、`mvnw` / `mvnw.cmd` 未列，而 §3.5 约束 2 又引用了 `./mvnw`；二是本机 Maven 为 3.9.11，规范基线为 3.9.9。若不在 S1 之前定死，会出现"文档要求 wrapper、仓库里没有 wrapper"的空档。

## 2. 选项与代价

### 2.1 构建入口

| 选项 | 代价 |
|---|---|
| **Maven Wrapper（`mvnw` / `mvnw.cmd` + `.mvn/wrapper/`），锁定 3.9.9** | 仓库多三个文件；首次运行需联网下载 Maven 发行包 |
| 要求开发机预装 Maven 3.9.9 | 与本机现状（3.9.11）冲突，需改全局安装；新机器克隆后不能"5 分钟跑起" |
| 改用 Gradle Wrapper | 与 GOV-000 §1.5 的技术基线冲突，且统计类构建收益不明显 |

**决策**：Maven Wrapper，锁定 3.9.9。全局 Maven 版本从此与本项目无关，`make` 内部只调 `./mvnw`。

### 2.2 Maven 工程结构

| 选项 | 代价 |
|---|---|
| **单模块 `backend/`，按包纵切领域** | 模块间边界靠 ArchUnit 约束，而非物理隔离；好处是构建快、依赖简单 |
| 多模块（`backend/core`、`backend/api`、`backend/infra`） | 物理边界更强，但每次改动跨三个 pom，单人开发的收益不抵成本；且与 §3.3 骨架不符 |

**决策**：单模块，`io.github.jameshome.galley.modules.{material,stock,stats,identity}` 按领域纵切，边界由 ArchUnit 强制。

### 2.3 前端位置

| 选项 | 代价 |
|---|---|
| **同仓 `frontend/`，pnpm + Vite** | 前后端契约同步靠同一次提交保证；仓库体积增大 |
| 独立仓库 | 契约漂移风险高，与 §5.5「契约同步」的要求相反 |

**决策**：同仓。

## 3. 决策

采用 GOV-000 §3.3 的目录骨架，并作两处补入：

1. 在 `backend/` 下增加 `mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`，与已有的 `.mvn/{maven.config,jvm.config}` 并列。`maven-wrapper.properties` 中 `distributionUrl` 指向 Maven 3.9.9。
2. `.github/workflows/ci.yml` 一律通过 `./mvnw` 调用，不使用 runner 预装的 Maven。

除此之外不改动骨架：新增一级目录仍需 ADR。

## 4. 后果

**好的**：克隆后无需装 Maven；本机 3.9.11 与 CI 的 Maven 版本差异消失；`make` 成为唯一命令入口的前提成立。

**坏的**：首次构建需下载 Maven 发行包，离线环境需预置 `.m2` 与 wrapper 缓存；`mvnw` 在 Windows 下需 `mvnw.cmd`，两条命令的换行符必须固定（`.gitattributes` 强制 LF）。

**需要后续偿还的**：若 Maven 3.9.9 出现安全通告，需同步升级 wrapper 与 `pom.xml` 的 `maven.compiler` 相关配置，并在迭代记录中留痕。
