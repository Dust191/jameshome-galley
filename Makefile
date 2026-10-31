SHELL := /bin/bash
COMPOSE := docker compose -f infra/compose/compose.dev.yml
MVN := ./mvnw
VM_HOST ?= dust@192.168.91.128
VM_PATH ?= /home/dust/jameshome-galley

.PHONY: help setup dev web test lint build up down sync check commit-lint

help:
	@echo "setup        一次性准备：工具链检查、.env、前端依赖、镜像"
	@echo "dev          起数据库并在前台运行后端"
	@echo "web          起前端开发服务器"
	@echo "test         后端测试 + 前端类型检查"
	@echo "lint         代码风格与静态检查"
	@echo "build        后端打包 + 前端构建"
	@echo "up / down    全容器化启动 / 停止"
	@echo "sync         同步工作区到虚拟机（容器操作前置）"

setup:
	@bash scripts/check-env.sh
	@test -f .env || cp .env.example .env
	cd frontend && pnpm install
	$(COMPOSE) pull

dev:
	$(COMPOSE) up -d db
	cd backend && $(MVN) spring-boot:run

web:
	cd frontend && pnpm dev

test:
	cd backend && $(MVN) -q test
	cd frontend && pnpm exec tsc --noEmit

lint:
	cd backend && $(MVN) -q spotless:check
	cd frontend && pnpm exec prettier --check .

build:
	cd backend && $(MVN) -q -DskipTests package
	cd frontend && pnpm build

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

# 容器相关操作在开发机不可用时使用：同步后再到虚拟机上执行
sync:
	rsync -az --delete \
		--exclude '.git/' --exclude 'node_modules/' --exclude 'target/' \
		--exclude '.env' --exclude '.workbuddy/' --exclude 'dist/' \
		./ $(VM_HOST):$(VM_PATH)/

commit-lint:
	@bash scripts/check-signoff.sh

check: lint test
