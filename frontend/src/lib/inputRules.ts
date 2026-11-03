/**
 * 数量输入的钳位规则。
 *
 * <p>antd 的 `InputNumber` 带 `min` / `precision` 时会在失焦时**悄悄**把值改成合法值——
 * 用户填了 `-3` 回头一看变成 0，填了 `1.23456` 变成 `1.235`，没有任何解释。
 * 这里把"改写"变成"改写 + 说明"：值被修正时返回原因，由组件显示红字。
 */

export type ClampedResult = {
  /** 修正后的值（字符串形式，供输入框显示）；无需修正时为 null */
  value: string | null
  /** 被改写时的说明；未改写为 null */
  reason: string | null
  /** 解析失败等非法输入（提交时由表单校验兜底报错） */
  invalid: boolean
}

/** 三位小数；超出的部分四舍五入。 */
function round3(value: number): string {
  return value.toFixed(3)
}

/**
 * @param raw 输入框里的原始字符串
 * @param allowNegative 盘点调整允许负数，入库/出库不允许
 * @param allowZero 安全库存一类的字段允许 0（表示不设预警线）
 */
export function clampQty(raw: string, allowNegative: boolean, allowZero = false): ClampedResult {
  const text = raw.trim()
  if (text === '') {
    return { value: null, reason: null, invalid: false }
  }

  const num = Number(text)
  if (Number.isNaN(num)) {
    return { value: null, reason: `「${text}」不是数字`, invalid: true }
  }
  if (num === 0 && !allowZero) {
    return { value: null, reason: '数量不能为 0', invalid: true }
  }
  if (!allowNegative && num < 0) {
    return {
      value: '0.000',
      reason: '不能为负数，已归零；如需调整库存请选择"盘点调整"',
      invalid: false,
    }
  }
  const decimals = (text.split('.')[1] ?? '').length
  if (decimals > 3) {
    return {
      value: round3(num),
      reason: `最多三位小数，已四舍五入为 ${round3(num)}`,
      invalid: false,
    }
  }
  // 合法值也统一补足三位小数展示，但不算"被改写"
  return { value: null, reason: null, invalid: false }
}
