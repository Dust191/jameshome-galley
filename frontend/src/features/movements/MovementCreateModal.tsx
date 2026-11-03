import { useEffect, useRef, useState } from 'react'
import {
  Alert,
  App as AntApp,
  DatePicker,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Typography,
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import type { Material, Movement, PageResult } from '../../lib/types'
import QtyInput from '../../components/QtyInput'

type FormValues = {
  materialId: string
  type: 'IN' | 'OUT' | 'ADJUST'
  quantity: number
  occurredAt: Dayjs
  note?: string
}

/**
 * 登记出入库弹窗。
 *
 * <p>数量框末尾自动带出所选物料的单位（如「袋」），省得用户回列表确认单位；
 * 换物料时单位随之更新。数量只填正数，符号由类型决定——盘点调整例外。
 */
export default function MovementCreateModal({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()
  const [type, setType] = useState<'IN' | 'OUT' | 'ADJUST'>('IN')
  const [error, setError] = useState<string | null>(null)

  // 幂等键：每次打开弹窗生成一次，重复提交/网络重试复用同一个
  const idempotencyRef = useRef(crypto.randomUUID())
  useEffect(() => {
    if (open) idempotencyRef.current = crypto.randomUUID()
  }, [open])

  // 监听所选物料，数量框后缀显示其单位
  const materialId = Form.useWatch('materialId', form)
  const materials = useQuery({
    queryKey: ['materials', 'options'],
    queryFn: () => api.get<PageResult<Material>>('/api/v1/materials?pageSize=100'),
  })
  const selected = (materials.data?.items ?? []).find((m) => m.id === materialId)
  const unitName = selected?.unitName

  const reset = () => {
    form.resetFields()
    setType('IN')
    setError(null)
  }

  const submit = useMutation({
    mutationFn: (values: FormValues) => {
      const magnitude = Math.abs(values.quantity)
      const signed =
        values.type === 'IN' ? magnitude : values.type === 'OUT' ? -magnitude : values.quantity
      return api.post<Movement>('/api/v1/movements', {
        materialId: values.materialId,
        type: values.type,
        quantity: signed.toFixed(3),
        occurredAt: values.occurredAt.toISOString(),
        note: values.note || null,
        idempotencyKey: idempotencyRef.current,
      })
    },
    onSuccess: (created) => {
      const typeName = created.type === 'IN' ? '入库' : created.type === 'OUT' ? '出库' : '盘点调整'
      message.success(
        `已登记：${typeName} ${created.quantity > 0 ? '+' : ''}${created.quantity.toFixed(3)}`,
      )
      queryClient.invalidateQueries({ queryKey: ['movements'] })
      queryClient.invalidateQueries({ queryKey: ['materials'] })
      queryClient.invalidateQueries({ queryKey: ['balances'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      onClose()
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        if (e.field === 'quantity' || e.field === 'materialId') {
          form.setFields([{ name: e.field as keyof FormValues, errors: [e.message] }])
        } else {
          setError(e.message)
        }
      } else {
        setError('登记失败，请稍后重试')
      }
    },
  })

  return (
    <Modal
      title="登记出入库"
      open={open}
      width={520}
      okText="提交"
      cancelText="取消"
      confirmLoading={submit.isPending}
      destroyOnClose
      afterClose={reset}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Form
        form={form}
        layout="vertical"
        requiredMark
        initialValues={{ type: 'IN', occurredAt: dayjs() }}
        onFinish={(values) => submit.mutate(values)}
        disabled={submit.isPending}
      >
        <Form.Item label="类型" name="type" rules={[{ required: true }]}>
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            onChange={(e) => setType(e.target.value)}
            options={[
              { label: '入库', value: 'IN' },
              { label: '出库', value: 'OUT' },
              { label: '盘点调整', value: 'ADJUST' },
            ]}
          />
        </Form.Item>

        <Form.Item
          label="物料"
          name="materialId"
          rules={[{ required: true, message: '请选择物料' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="按名称搜索"
            options={(materials.data?.items ?? []).map((m) => ({
              label: `${m.name}${m.spec ? ` (${m.spec})` : ''} · 当前 ${m.currentStock.toFixed(3)} ${m.unitName}`,
              value: m.id,
            }))}
          />
        </Form.Item>

        <Form.Item
          label="数量"
          name="quantity"
          rules={[
            { required: true, message: '请输入数量' },
            () => ({
              validator(_, value) {
                if (value == null) return Promise.resolve()
                if (value === 0) return Promise.reject(new Error('数量不能为 0'))
                const decimals = (String(value).split('.')[1] ?? '').length
                if (decimals > 3) return Promise.reject(new Error('最多三位小数'))
                if (type !== 'ADJUST' && value < 0) {
                  return Promise.reject(new Error('请填正数；系统按所选类型自动记账'))
                }
                return Promise.resolve()
              },
            }),
          ]}
          extra={
            type === 'OUT'
              ? '填实际领取数量即可，系统按出库记账。允许库存为负（先领后补）。'
              : type === 'ADJUST'
                ? '填差额：盘盈为正、盘亏为负。'
                : '填实际入库数量。'
          }
        >
          <QtyInput allowNegative={type === 'ADJUST'} addonAfter={unitName ?? '单位'} />
        </Form.Item>

        <Form.Item
          label="发生时间"
          name="occurredAt"
          rules={[{ required: true, message: '请选择发生时间' }]}
          extra="可与登记时间不同，用于补录"
        >
          <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm" />
        </Form.Item>

        <Form.Item label="备注" name="note">
          <Input.TextArea rows={2} maxLength={200} showCount placeholder="如：午餐领用" />
        </Form.Item>

        <Typography.Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 12 }}>
          本次提交的防重复标识：<code>{idempotencyRef.current.slice(0, 8)}</code>
          （网络失败重试不会重复记账）
        </Typography.Paragraph>
      </Form>
    </Modal>
  )
}
