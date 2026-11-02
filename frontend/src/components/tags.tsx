import { Tag } from 'antd'
import { MATERIAL_STATUS_LABEL, MOVEMENT_TYPE_LABEL } from '../lib/format'
import type { MaterialStatus, MovementType } from '../lib/types'

/**
 * 状态与徽标（DES-003 §4.3，返工要求 6）。
 *
 * <p>物料与账号的启用状态一律显示"启用/停用/已归档"——不再用"正常"，
 * 避免与库存预警的"负库存"红徽标混在同行时产生"到底正不正常"的歧义。
 * 预警只由结存徽标表达。
 */

const MATERIAL_STATUS_COLOR: Record<MaterialStatus, string> = {
  active: 'blue',
  inactive: 'default',
  archived: 'default',
}

export function MaterialStatusTag({ status }: { status: MaterialStatus }) {
  return <Tag color={MATERIAL_STATUS_COLOR[status]}>{MATERIAL_STATUS_LABEL[status]}</Tag>
}

export function AccountStatusTag({ status }: { status: 'active' | 'disabled' }) {
  return status === 'active' ? <Tag color="blue">启用</Tag> : <Tag>已停用</Tag>
}

export function InviteStatusTag({ status }: { status: 'unused' | 'used' | 'disabled' }) {
  if (status === 'unused') return <Tag color="blue">未使用</Tag>
  if (status === 'used') return <Tag color="green">已使用</Tag>
  return <Tag>已禁用</Tag>
}

const MOVEMENT_TYPE_COLOR: Record<MovementType, string> = {
  IN: 'green',
  OUT: 'orange',
  ADJUST: 'blue',
  REVERSAL: 'default',
}

export function MovementTypeTag({ type }: { type: MovementType }) {
  return <Tag color={MOVEMENT_TYPE_COLOR[type]}>{MOVEMENT_TYPE_LABEL[type]}</Tag>
}

/** 严重度 3/2/1/0 对应 负库存 / 缺货 / 待补货 / 长期未动。 */
export function SeverityTag({ severity, reason }: { severity: number; reason?: string }) {
  if (severity === 3) return <Tag color="red">{reason ?? '负库存'}</Tag>
  if (severity === 2) return <Tag color="red">{reason ?? '缺货'}</Tag>
  if (severity === 1) return <Tag color="orange">{reason ?? '待补货'}</Tag>
  return <Tag color="default">{reason ?? '长期未动'}</Tag>
}
