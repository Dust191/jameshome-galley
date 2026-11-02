import { useEffect, useRef, useState } from 'react'
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
 * 数量输入（修复版）。
 *
 * <p>两个设计决定：
 *
 * <ul>
 *   <li><b>输入即同步表单</b>：只要文本能解析为数字就立刻 {@code onChange}——
 *       否则用户填 5、10 这类"本来就合法"的数时，表单里根本没有值，
 *       提交时反报"请输入数量"（返工后首版正是这个缺陷）。
 *   <li><b>失焦只做两件事</b>：把值归一化为三位小数展示；被改写时红字说明原因。
 *       提示是解释，不是校验——校验由表单层的 {@code qtyRules} 兜底。
 * </ul>
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
  // 正在输入时外部 value 回流（同一数值）不得覆盖输入框，否则 "5." 会被改回 "5"
  const editingRef = useRef(false)

  // 外部回填（编辑表单 setFieldsValue）同步到输入框
  useEffect(() => {
    if (editingRef.current) return
    const parsed = Number(text)
    if (text !== '' && !Number.isNaN(parsed) && parsed === value) return
    setText(value == null ? '' : String(value))
  }, [value])

  const emit = (raw: string) => {
    const trimmed = raw.trim()
    if (trimmed === '') {
      onChange?.(null)
      return
    }
    const num = Number(trimmed)
    // 解析不了的中间态（如单独的 "-"）不更新表单，等继续输入或失焦兜底
    if (!Number.isNaN(num) && Number.isFinite(num)) {
      onChange?.(num)
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
        onFocus={() => {
          editingRef.current = true
        }}
        onChange={(e) => {
          const raw = e.target.value
          setText(raw)
          // 重新输入即视为在修正，清掉上一次的提示
          setClampedReason(null)
          setInvalid(false)
          emit(raw)
        }}
        onBlur={() => {
          editingRef.current = false
          const result = clampQty(text, allowNegative)
          setClampedReason(result.reason)
          setInvalid(result.invalid)

          if (result.invalid) return // 非法输入交表单校验报错

          if (result.value != null) {
            // 值被修正：改写显示并同步表单
            setText(result.value)
            onChange?.(Number(result.value))
            return
          }

          const trimmed = text.trim()
          if (trimmed === '') {
            onChange?.(null)
            return
          }
          const num = Number(trimmed)
          if (Number.isNaN(num) || !Number.isFinite(num)) return
          const normalized = num.toFixed(3)
          setText(normalized)
          onChange?.(Number(normalized))
        }}
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
