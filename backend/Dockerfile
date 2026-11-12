# 后端镜像：多阶段构建，运行层只带 JRE 与 jar。
# 构建上下文是 backend/，由 release.yml 传入。

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# 先只拷贝构建描述，命中缓存时不必每次重下依赖
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
# go-offline 只是预热缓存，个别插件解析失败不应中断构建
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests dependency:go-offline || true

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre-alpine

# curl 供编排里的健康检查使用；非特权账号运行应用
RUN apk add --no-cache curl \
    && addgroup -S galley \
    && adduser -S -G galley -h /app galley

WORKDIR /app
COPY --from=build /build/target/galley-*.jar /app/app.jar

ENV TZ=Asia/Shanghai
USER galley
EXPOSE 8080

# 容器内存上限由编排决定，堆按比例取；OOM 时直接退出，交给 restart 策略
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
