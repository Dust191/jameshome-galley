# 前端镜像：pnpm 构建后交给 nginx 托管，SPA 路由回退由 nginx.conf 处理。
# 构建上下文是 frontend/。

FROM node:22-alpine AS build
WORKDIR /app

# 锁死 pnpm 版本，与 packageManager 字段一致
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
