import { useState } from 'react'
import { Input, Typography } from 'antd'
import { clampQty } from '../lib/inputRules'

type Props = {
  value?: number | null
  onChange?: (value: number | null) => void
  /** 盘点调整允许负数 */
  allowNegative?: boolean
  placeholder?: string
  disabled?: boolean
  autoFocus?: boolean
}

/**
 * 数量输入：不静默改写（返工要求 4）。
 *
 * <p>antd `InputNumber` 的 `min` / `precision` 会在失焦时悄悄钳位，用户没有任何感知。
 * 这里改用普通 `Input` + 失焦检查：值被修正时，在输入框下方用红字说明原因
 * （"不能为负数，已归零…"、"最多三位小数，已四舍五入…"）。
 */
export default function QtyInput({
  value,
  onChange,
  allowNegative = false,
  placeholder,
  disabled,
  autoFocus,
}: Props) {
  const [text, setText] = useState(value == null ? '' : String(value))
  const [clampedReason, setClampedReason] = useState<string | null>(null)
  const [invalid, setInvalid] = useState(false)

  const handleBlur = () => {
    const result = clampQty(text, allowNegative)
    setClampedReason(result.reason)
    setInvalid(result.invalid)
    if (result.value != null) {
      setText(result.value)
      onChange?.(Number(result.value))
    }
  }

  return (
    <div>
      <Input
        value={text}
        inputMode="decimal"
        placeholder={placeholder ?? '0.000'}
        disabled={disabled}
        autoFocus={autoFocus}
        status={invalid ? 'error' : undefined}
        onChange={(e) => {
          setText(e.target.value)
          // 重新输入即视为在修正，清掉上一次的提示
          setClampedReason(null)
          setInvalid(false)
        }}
        onBlur={handleBlur}
      />
      {clampedReason && (
        <Typography.Text type="danger" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
          {clampedReason}
        </Typography.Text>
      )}
    </div>
  )
}

/** 表单提交前的硬校验：与 QtyInput 的提示同源，防绕过。 */
export function qtyRules(allowNegative: boolean) {
  return [
    {
      validator: (_: unknown, value: number | null | undefined) => {
        if (value == null) return Promise.reject(new Error('请输入数量'))
        if (value === 0) return Promise.reject(new Error('数量不能为 0'))
        if (!allowNegative && value < 0) {
          return Promise.reject(new Error('不能为负数；如需调整库存请选择"盘点调整"'))
        }
        const decimals = (String(value).split('.')[1] ?? '').length
        if (decimals > 3) return Promise.reject(new Error('最多三位小数'))
        return Promise.resolve()
      },
    },
  ]
}
