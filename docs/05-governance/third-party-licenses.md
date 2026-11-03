---
title: 第三方依赖许可证清单
doc_id: GOV-005
status: approved
owner: "@Dust191"
reviewers: ["@Dust191"]
created: 2026-11-03
updated: 2026-11-03
next_review: 2027-02-03
iter: ITER-20261103-04
related: [GOV-000, GOV-004]
---

# GOV-005 第三方依赖许可证清单

这份文档回答一个问题：**项目依赖了哪些第三方包、各自是什么许可证、有没有超出 `CONTRIBUTING` §5 允许清单的例外。**

## 1. 生成方式与范围

| 项 | 内容 |
|---|---|
| 生成日期 | 2026-11-03 |
| 基线版本 | 0.1.0 |
| 后端 | `dependency:list -DincludeScope=runtime` 共 120 个制品，逐个读取本地 Maven 仓库 POM 的 `<licenses>` 节（父 POM 向上追溯 2 层） |
| 前端 | `pnpm licenses list --prod --json`，共 78 个包 |
| 更新时机 | 依赖升级或发版前重新生成；与 Dependabot 报告交叉核对（`GOV-000` §6.4） |

## 2. 汇总

| 许可证 | 后端（运行时） | 前端（生产依赖） |
|---|---|---|
| Apache-2.0 | 106 | 0 |
| MIT | 3 | 78 |
| BSD-2-Clause | 1 | 0 |
| EPL-2.0 | 3 | 0 |
| EDL 1.0（BSD-3 风格） | 2 | 0 |
| CC0 1.0（公有领域） | 1 | 0 |
| Bouncy Castle（MIT 风格） | 1 | 0 |

全部为宽松许可，无 copyleft 传染风险；例外与归属见 §3。

## 3. 需要说明的五个点

| 依赖 | 现象 | 结论 |
|---|---|---|
| `com.github.jsqlparser:jsqlparser` | 双许可：LGPL-2.1 **或** Apache-2.0 | 按 Apache-2.0 选项使用，符合允许清单 |
| `org.springframework.session:*` | POM 的 license 字段写「Broadcom Foundation License」 | Spring 4.1.x POM 的占位写法，项目实际许可为 Apache-2.0（见 spring.io 项目页）；法律级确认以源码仓库 LICENSE 为准 |
| `org.bouncycastle:bcprov-jdk18on` | POM 无 licenses 节 | 随包 LICENSE 为 MIT 风格（Bouncy Castle License），源码仓库可查 |
| `jakarta.activation-api`、`jakarta.xml.bind-api` | EDL 1.0 | 即 BSD-3-Clause 风格，符合允许清单 |
| `org.hdrhistogram:HdrHistogram` | CC0 1.0 | 公有领域，比允许清单更宽松 |

## 4. 后端运行时依赖（120 个制品）

<details>
<summary>Apache-2.0（106 个）</summary>

com.baomidou:mybatis-plus-annotation:3.5.18、com.baomidou:mybatis-plus-core:3.5.18、com.baomidou:mybatis-plus-extension:3.5.18、com.baomidou:mybatis-plus-jsqlparser-common:3.5.18、com.baomidou:mybatis-plus-jsqlparser:3.5.18、com.baomidou:mybatis-plus-spring-boot-autoconfigure:3.5.18、com.baomidou:mybatis-plus-spring-boot-native-image:3.5.18、com.baomidou:mybatis-plus-spring-boot4-starter:3.5.18、com.baomidou:mybatis-plus-spring:3.5.18、com.baomidou:mybatis-plus:3.5.18、com.carrotsearch.thirdparty:simple-xml-safe:2.7.1、com.fasterxml.jackson.core:jackson-annotations:2.21、com.fasterxml.jackson.core:jackson-core:2.21.7、com.fasterxml.jackson.core:jackson-databind:2.21.7、com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.7、com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.7、com.fasterxml:classmate:1.7.3、com.google.errorprone:error_prone_annotations:2.50.0、com.google.guava:failureaccess:1.0.3、com.google.guava:guava:33.7.2-jre、com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava、com.google.j2objc:j2objc-annotations:3.1、com.squareup.okhttp3:okhttp-jvm:5.1.0、com.squareup.okhttp3:okhttp:5.1.0、com.squareup.okio:okio-jvm:3.15.0、com.squareup.okio:okio:3.15.0、com.zaxxer:HikariCP:7.0.2、commons-codec:commons-codec:1.21.0、commons-io:commons-io:2.20.0、commons-logging:commons-logging:1.3.6、io.micrometer:micrometer-commons:1.17.2、io.micrometer:micrometer-core:1.17.2、io.micrometer:micrometer-jakarta9:1.17.2、io.micrometer:micrometer-observation:1.17.2、io.minio:minio:8.6.0、io.swagger.core.v3:swagger-annotations-jakarta:2.2.55、io.swagger.core.v3:swagger-core-jakarta:2.2.55、io.swagger.core.v3:swagger-models-jakarta:2.2.55、jakarta.validation:jakarta.validation-api:3.1.1、org.apache.commons:commons-compress:1.28.0、org.apache.commons:commons-lang3:3.20.0、org.apache.logging.log4j:log4j-api:2.25.5、org.apache.logging.log4j:log4j-to-slf4j:2.25.5、org.apache.tomcat.embed:tomcat-embed-core:11.0.27、org.apache.tomcat.embed:tomcat-embed-el:11.0.27、org.apache.tomcat.embed:tomcat-embed-websocket:11.0.27、org.flywaydb:flyway-core:12.4.0、org.flywaydb:flyway-database-postgresql:12.4.0、org.hibernate.validator:hibernate-validator:9.1.5.Final、org.jboss.logging:jboss-logging:3.6.3.Final、org.jetbrains.kotlin:kotlin-stdlib:2.3.21、org.jetbrains:annotations:13.0、org.jspecify:jspecify:1.0.1、org.mybatis:mybatis-spring:4.1.0、org.mybatis:mybatis:3.5.19、org.springdoc:springdoc-openapi-starter-common:3.2.0、org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0、org.springframework.boot:spring-boot-actuator-autoconfigure:4.1.2、org.springframework.boot:spring-boot-actuator:4.1.2、org.springframework.boot:spring-boot-autoconfigure:4.1.2、org.springframework.boot:spring-boot-flyway:4.1.2、org.springframework.boot:spring-boot-health:4.1.2、org.springframework.boot:spring-boot-http-converter:4.1.2、org.springframework.boot:spring-boot-jackson:4.1.2、org.springframework.boot:spring-boot-jdbc:4.1.2、org.springframework.boot:spring-boot-micrometer-metrics:4.1.2、org.springframework.boot:spring-boot-micrometer-observation:4.1.2、org.springframework.boot:spring-boot-persistence:4.1.2、org.springframework.boot:spring-boot-security:4.1.2、org.springframework.boot:spring-boot-servlet:4.1.2、org.springframework.boot:spring-boot-sql:4.1.2、org.springframework.boot:spring-boot-starter-actuator:4.1.2、org.springframework.boot:spring-boot-starter-flyway:4.1.2、org.springframework.boot:spring-boot-starter-jackson:4.1.2、org.springframework.boot:spring-boot-starter-jdbc:4.1.2、org.springframework.boot:spring-boot-starter-logging:4.1.2、org.springframework.boot:spring-boot-starter-micrometer-metrics:4.1.2、org.springframework.boot:spring-boot-starter-security:4.1.2、org.springframework.boot:spring-boot-starter-tomcat-runtime:4.1.2、org.springframework.boot:spring-boot-starter-tomcat:4.1.2、org.springframework.boot:spring-boot-starter-validation:4.1.2、org.springframework.boot:spring-boot-starter-web:4.1.2、org.springframework.boot:spring-boot-starter:4.1.2、org.springframework.boot:spring-boot-tomcat:4.1.2、org.springframework.boot:spring-boot-transaction:4.1.2、org.springframework.boot:spring-boot-validation:4.1.2、org.springframework.boot:spring-boot-web-server:4.1.2、org.springframework.boot:spring-boot-webmvc:4.1.2、org.springframework.boot:spring-boot:4.1.2、org.springframework.security:spring-security-config:7.1.2、org.springframework.security:spring-security-core:7.1.2、org.springframework.security:spring-security-crypto:7.1.2、org.springframework.security:spring-security-web:7.1.2、org.springframework:spring-aop:7.0.10、org.springframework:spring-beans:7.0.10、org.springframework:spring-context:7.0.10、org.springframework:spring-core:7.0.10、org.springframework:spring-expression:7.0.10、org.springframework:spring-jdbc:7.0.10、org.springframework:spring-tx:7.0.10、org.springframework:spring-web:7.0.10、org.springframework:spring-webmvc:7.0.10、org.xerial.snappy:snappy-java:1.1.10.9、org.yaml:snakeyaml:2.6、tools.jackson.core:jackson-core:3.1.7、tools.jackson.core:jackson-databind:3.1.7
</details>

非 Apache-2.0 的 14 个：

| 制品 | 许可证 |
|---|---|
| `org.postgresql:postgresql:42.7.14` | BSD-2-Clause |
| `org.springframework.session:spring-session-core:4.1.1` | Apache-2.0（见 §3） |
| `org.springframework.session:spring-session-jdbc:4.1.1` | Apache-2.0（见 §3） |
| `jakarta.activation:jakarta.activation-api:2.1.4` | EDL 1.0（BSD-3 风格） |
| `ch.qos.logback:logback-classic:1.5.38` | EPL-2.0 |
| `ch.qos.logback:logback-core:1.5.38` | EPL-2.0 |
| `jakarta.annotation:jakarta.annotation-api:3.0.0` | EPL-2.0 |
| `jakarta.xml.bind:jakarta.xml.bind-api:4.0.5` | EDL 1.0（BSD-3 风格） |
| `com.github.jsqlparser:jsqlparser:5.4` | LGPL-2.1 或 Apache-2.0（取后者） |
| `org.checkerframework:checker-qual:3.55.1` | MIT |
| `org.slf4j:jul-to-slf4j:2.0.20` | MIT |
| `org.slf4j:slf4j-api:2.0.20` | MIT |
| `org.hdrhistogram:HdrHistogram:2.2.2` | CC0 1.0 |

## 5. 前端生产依赖（78 个包）

<details>
<summary>MIT（78 个）</summary>

@ant-design/colors（7.2.1）、@ant-design/cssinjs-utils（1.1.3）、@ant-design/cssinjs（1.24.0）、@ant-design/fast-color（2.0.6）、@ant-design/icons-svg（4.6.0）、@ant-design/icons（5.5.2）、@ant-design/react-slick（1.1.2）、@babel/runtime（7.29.10）、@ctrl/tinycolor（3.6.1）、@emotion/hash（0.8.0）、@emotion/unitless（0.7.5）、@rc-component/async-validator（5.1.2）、@rc-component/color-picker（2.0.1）、@rc-component/context（1.4.0）、@rc-component/mini-decimal（1.1.4）、@rc-component/mutate-observer（1.1.0）、@rc-component/portal（1.1.2）、@rc-component/qrcode（1.0.1）、@rc-component/tour（1.15.1）、@rc-component/trigger（2.3.1）、@remix-run/router（1.23.4）、@tanstack/query-core（5.62.0）、@tanstack/react-query（5.62.0）、antd（5.22.5）、classnames（2.5.1）、compute-scroll-into-view（3.1.1）、copy-to-clipboard（3.3.3）、csstype（3.2.3）、dayjs（1.11.13）、js-tokens（4.0.0）、json2mq（0.2.0）、loose-envify（1.4.0）、rc-cascader（3.30.0）、rc-checkbox（3.3.0）、rc-collapse（3.9.0）、rc-dialog（9.6.0）、rc-drawer（7.2.0）、rc-dropdown（4.2.1）、rc-field-form（2.7.1）、rc-image（7.11.1）、rc-input-number（9.3.0）、rc-input（1.6.4）、rc-mentions（2.17.0）、rc-menu（9.16.1）、rc-motion（2.9.5）、rc-notification（5.6.4）、rc-overflow（1.5.0）、rc-pagination（5.0.0）、rc-picker（4.8.3）、rc-progress（4.0.0）、rc-rate（2.13.1）、rc-resize-observer（1.4.3）、rc-segmented（2.5.0）、rc-select（14.16.8）、rc-slider（11.1.9）、rc-steps（6.0.1）、rc-switch（4.1.0）、rc-table（7.49.0）、rc-tabs（15.4.0）、rc-textarea（1.8.2）、rc-tooltip（6.2.1）、rc-tree-select（5.24.5）、rc-tree（5.10.1）、rc-upload（4.8.1）、rc-util（5.44.4）、rc-virtual-list（3.19.2）、react-dom（18.3.1）、react-is（18.3.1）、react-router-dom（6.30.6）、react-router（6.30.6）、react（18.3.1）、resize-observer-polyfill（1.5.1）、scheduler（0.23.2）、scroll-into-view-if-needed（3.1.0）、string-convert（0.2.1）、stylis（4.4.0）、throttle-debounce（5.0.2）、toggle-selection（1.0.6）
</details>

## 6. 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-03 | 初版建立：0.1.0 基线，后端 120 制品 / 前端 78 包 | ITER-20261103-04 |
