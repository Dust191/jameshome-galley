import type { InviteStatus, MaterialStatus, MovementType, Role } from './types'

/**
 * 全部对外文案的单一来源。
 *
 * <p>禁止在组件里直接写 "IN"、"active" 这类内部取值——用户看到的是"出库"和"启用"，
 * 不是数据库枚举。改文案只改这里，全文一致。
 */

export const MATERIAL_STATUS_LABEL: Record<MaterialStatus, string> = {
  active: '启用',
  inactive: '停用',
  archived: '已归档',
}

export const MOVEMENT_TYPE_LABEL: Record<MovementType, string> = {
  IN: '入库',
  OUT: '出库',
  ADJUST: '盘点调整',
  REVERSAL: '冲正',
}

export const ROLE_LABEL: Record<Role, string> = {
  admin: '管理员',
  editor: '录入员',
  viewer: '只读',
}

export const INVITE_STATUS_LABEL: Record<InviteStatus, string> = {
  unused: '未使用',
  used: '已使用',
  disabled: '已禁用',
}

/** 库存预警状态筛选项，值与后端 `stockState` 参数一致。 */
export const STOCK_STATE_OPTIONS = [
  { label: '待补货（有货但低于安全线）', value: 'low' },
  { label: '缺货', value: 'zero' },
  { label: '负库存', value: 'negative' },
  { label: '长期未动', value: 'stale' },
  { label: '全部预警', value: 'alert' },
]

export function stockStateLabel(value: string | undefined | null): string {
  return STOCK_STATE_OPTIONS.find((o) => o.value === value)?.label ?? '—'
}

/** 空值统一显示 `—`，不显示空白或 null（DES-003 §4.1）。 */
export function orDash(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

/** 数量按三位小数展示；表格里配等宽数字字体。 */
export function fmtQty(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = typeof value === 'string' ? Number(value) : value
  if (Number.isNaN(num)) return '—'
  return num.toFixed(3)
}

/** 展示时间用上海时区；后端存 UTC，直接展示会差 8 小时。 */
export function fmtTime(iso: string | null | undefined, withTime = true): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  const ymd = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  if (!withTime) return ymd
  return `${ymd} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
