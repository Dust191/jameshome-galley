import { defineConfig, devices } from '@playwright/test'

/**
 * E2E 配置（GOV-000 §5.5 三条主流程）。
 *
 * 跑之前先启动前端 dev server，并通过 E2E_ADMIN_PASSWORD 传入当前管理员密码
 * （开发环境每次启动都会重生成，见 DEV-001）。
 */
export default defineConfig({
  testDir: './e2e',
  // 产物放临时目录：项目内的批量删除会触发安全策略
  outputDir: process.env.PLAYWRIGHT_OUTPUT_DIR ?? 'test-results',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173',
    locale: 'zh-CN',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
