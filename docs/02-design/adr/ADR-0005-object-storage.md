---
title: 对象存储采用可替换实现：开发用 MinIO，生产用 OSS
doc_id: ADR-0005
status: in_review
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-02
updated: 2026-11-02
next_review: 2027-01-30
iter: ITER-20261102-01
related: [DES-001, OPS-001, ADR-0001]
---

# ADR-0005 对象存储采用可替换实现：开发用 MinIO，生产用 OSS

这份文档回答一个问题：**物料图片存哪、怎么做到换存储不改业务代码。**

- **状态**：accepted（开发者 2026-11-02 裁定）
- **取代 / 被取代**：无

## 1. 背景

`DES-001` 的 `material.image_url` 只存 key，不存公网地址；`OPS-001` 的生产拓扑写的是阿里云 OSS。但 S3 开发阶段既没有 OSS 账号，也不该让本地开发依赖云服务——UAT-1 又明确要求"新建物料（含图片）成功"，所以必须有一个本地落点。

开发者的决定是：虚拟机里跑 MinIO 容器，生产接 OSS。

## 2. 选项与代价

| 选项 | 代价 |
|---|---|
| A 本地目录 + 后端静态托管 | 最简单，但与生产对象存储行为不一致（无预签名、无分片），换 OSS 时要改上传逻辑与 URL 生成 |
| B MinIO（采用） | 多一个容器；但与 S3 协议兼容，接口形态与 OSS 基本一致，换实现只改配置 |
| C 只存外链 URL，不做上传 | UAT-1 验不过；且图片会散落在外部，无法治理 |

## 3. 决策

| 层 | 取值 |
|---|---|
| 开发 / 测试 | 虚拟机内 MinIO 容器，桶 `galley-material`，API 端口 9000、控制台 9001 |
| 生产 | 阿里云 OSS，桶名待 S5 定 |
| 业务代码 | 只依赖 `ObjectStorage` 接口：`put(key, bytes, contentType)`、`get(key)`、`delete(key)`、`publicUrl(key)` |
| 实现 | `MinioObjectStorage`（S3 协议）；生产切 OSS 时新增 `OssObjectStorage`，只改装配 |
| 数据库 | `material.image_url` 存 **key**（如 `material/2026/11/uuid.webp`），不存完整 URL |

`publicUrl(key)` 的返回值由实现决定：MinIO 返回 `http://<vm>:9000/galley-material/<key>`，OSS 返回 CDN 域名。业务代码不得拼接 URL。

## 4. 后果

**好的**：本地开发不依赖云账号；UAT-1 能真验；换 OSS 时业务代码零改动。

**坏的**：MinIO 与 OSS 在预签名、跨域、图片处理上仍有差异，S5 切生产时要跑一次真实上传验证，不能只看接口兼容。

**需要后续偿还的**：`OssObjectStorage` 尚未实现；图片压缩（webp 转码）MVP 不做，上传原图。

**已知环境问题**：虚拟机 Docker 默认镜像源 `docker.m.daocloud.io` 对 `minio/minio` 返回 403，`quay.io` 已不接受匿名拉取，`dl.min.io` 的二进制返回 410。当前可通过华为镜像站拉取同镜像（`swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/minio/minio:latest`）。换机器时优先修镜像源，不要把这条写死进脚本。

## 5. 与既有文档的关系

| 文档 | 处理 |
|---|---|
| `DES-001` §3.4 | `image_url` 语义明确为对象 key，已在字段说明中体现 |
| `OPS-001` §1 | 生产仍为 OSS；开发环境补 MinIO 一行 |
| `DEV-001` | 已补 MinIO 启动步骤与镜像源问题 |
| `GOV-000` §1.5 | 无需改动，存储属实现选择 |
