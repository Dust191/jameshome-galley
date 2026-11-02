/** 与后端 DTO 一一对应。字段名保持与接口一致，减少对照成本。 */

export type Role = 'admin' | 'editor' | 'viewer'
export type MaterialStatus = 'active' | 'inactive' | 'archived'
export type MovementType = 'IN' | 'OUT' | 'ADJUST' | 'REVERSAL'
export type InviteStatus = 'unused' | 'used' | 'disabled'

export type Me = {
  id: string
  username: string
  displayName: string
  role: Role
  status: 'active' | 'disabled'
  mustChangePassword: boolean
}

export type PageResult<T> = {
  items: T[]
  page: number
  pageSize: number
  total: number
  capped: boolean
}

export type Category = {
  id: string
  name: string
  parentId: string | null
  sortOrder: number
  status: 'active' | 'inactive'
}

export type Unit = {
  id: string
  name: string
  sortOrder: number
  status: 'active' | 'inactive'
}

export type Material = {
  id: string
  code: string | null
  name: string
  categoryId: string
  categoryName: string
  unitId: string
  unitName: string
  spec: string | null
  safetyStock: number
  shelfLifeDays: number | null
  imageKey: string | null
  imageUrl: string | null
  note: string | null
  status: MaterialStatus
  version: number
  currentStock: number
}

export type Movement = {
  id: string
  materialId: string
  materialName: string | null
  unitName: string | null
  type: MovementType
  quantity: number
  occurredAt: string
  operatorName: string | null
  note: string | null
  reversed: boolean
  reversalOf: string | null
}

export type Overview = {
  materialCount: number
  lowStockCount: number
  zeroStockCount: number
  staleCount: number
  completenessRate: string
}

export type CategoryShare = {
  categoryName: string
  materialCount: number
  ratio: string
}

export type TrendPoint = {
  date: string
  inCount: number
  outCount: number
}

export type TopOutItem = {
  materialName: string
  unitName: string
  outCount: number
  totalQuantity: number
}

export type AlertList = {
  items: AlertItem[]
  total: number
}

export type AlertItem = {
  materialName: string
  unitName: string
  quantity: string
  safetyStock: string
  severity: number
  reason: string
}

export type UserView = {
  id: string
  username: string
  displayName: string
  role: Role
  status: 'active' | 'disabled'
  passwordUpdatedAt: string | null
}

export type InviteView = {
  id: string
  code: string
  status: InviteStatus
  usedBy: string | null
  usedAt: string | null
  createdAt: string | null
}
