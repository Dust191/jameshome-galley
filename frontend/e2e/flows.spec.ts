import { expect, test, type Page } from '@playwright/test'

/**
 * G3 的三条主流程（GOV-000 §5.5）：
 *   1. 建档 → 入库 → 查看结存
 *   2. 冲正 → 结存回退
 *   3. 导入 → 错误报告
 *
 * 前置：前端 dev server 已启动；管理员密码通过 E2E_ADMIN_PASSWORD 传入。
 * 数据隔离：每次用时间戳生成物料名，跑完不清理（开发库允许留痕）。
 */
const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? ''

/** antd 会在两字按钮里插空格（「登 录」），匹配时容忍任意空白 */
const btn = (text: string) => new RegExp(text.split('').join('\\s*'))

test.skip(!adminPassword, '需设置 E2E_ADMIN_PASSWORD（开发环境密码每次启动都会变）')

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill(adminPassword)
  await page.getByRole('button', { name: btn('登录') }).click()
  await expect(page.getByRole('heading', { name: '概览' })).toBeVisible()
}

async function pickFirstOption(page: Page) {
  // 用键盘选：页面里同时存在背景筛选器的 option，按角色取容易选中不可见的那个
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
}

async function createMaterial(page: Page, name: string) {
  await page.goto('/materials')
  await page.getByRole('button', { name: btn('新建物料') }).click()

  const modal = page.locator('.ant-modal-content').filter({ hasText: '新建物料' })
  await modal.locator('#name').fill(name)
  await modal.locator('#categoryId').click()
  await pickFirstOption(page)
  await modal.locator('#unitId').click()
  await pickFirstOption(page)
  await modal.locator('#safetyStock').fill('5')
  await modal.getByRole('button', { name: btn('保存') }).click()

  await expect(page.getByText('已建档')).toBeVisible()
}

async function addMovement(page: Page, name: string, quantity: string) {
  await page.goto('/movements')
  await page.getByRole('button', { name: btn('登记出入库') }).click()

  const modal = page.locator('.ant-modal-content').filter({ hasText: '登记出入库' })
  await modal.locator('#materialId').fill(name)
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await modal.locator('#quantity').fill(quantity)
  await modal.getByRole('button', { name: btn('提交') }).click()

  await expect(page.getByText(/已登记/)).toBeVisible()
}

test('建档 → 入库 → 查看结存', async ({ page }) => {
  const name = `E2E大米-${Date.now()}`
  await login(page)
  await createMaterial(page, name)

  await page.getByPlaceholder('按名称或自编码搜索').fill(name)
  await expect(page.getByRole('link', { name })).toBeVisible()

  await addMovement(page, name, '10')
  await expect(page.getByText(name).first()).toBeVisible()

  await page.goto('/materials')
  await page.getByPlaceholder('按名称或自编码搜索').fill(name)
  const row = page.getByRole('row').filter({ hasText: name })
  await expect(row.getByText('10.000')).toBeVisible()
})

test('冲正 → 结存回退', async ({ page }) => {
  const name = `E2E冲正-${Date.now()}`
  await login(page)
  await createMaterial(page, name)
  await addMovement(page, name, '10')

  await page.goto('/movements')
  const row = page.getByRole('row').filter({ hasText: name }).first()
  await row.getByRole('button', { name: btn('冲正') }).click()

  const modal = page.locator('.ant-modal-content').filter({ hasText: '冲正这条记录' })
  await modal.getByPlaceholder('例如：数量录错，需要更正').fill('E2E 自动测试，冲正回退')
  await modal.getByRole('button', { name: btn('确认冲正') }).click()

  await expect(page.getByText('已冲正，库存已回退')).toBeVisible()

  await page.goto('/materials')
  await page.getByPlaceholder('按名称或自编码搜索').fill(name)
  const materialRow = page.getByRole('row').filter({ hasText: name })
  await expect(materialRow.getByText('0.000')).toBeVisible()
})

test('导入 → 错误报告', async ({ page }) => {
  await login(page)
  await page.goto('/materials')
  await page.getByRole('button', { name: btn('导入') }).click()

  const modal = page.locator('.ant-modal-content').filter({ hasText: '导入物料' })
  const csv =
    '自编码,物料名,分类,单位,规格,安全库存,保质期(天),当前结存,状态,备注\r\n' +
    'E2E-IMP-1,,不存在分类,不存在单位,500g,1,180,0,active,缺名称\r\n'

  await modal.locator('input[type=file]').setInputFiles({
    name: 'e2e-import.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf-8'),
  })
  await modal.getByRole('button', { name: btn('开始导入') }).click()

  await expect(modal.getByText(/成功 0 行，失败 1 行/)).toBeVisible()
  await expect(modal.getByRole('cell', { name: '物料名', exact: true })).toBeVisible()
  await expect(modal.getByRole('cell', { name: '不能为空', exact: true })).toBeVisible()
  await expect(modal.getByRole('cell', { name: '2', exact: true })).toBeVisible()
})
