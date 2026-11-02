import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Radio,
  Result,
  Select,
  Space,
  Typography,
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api, ApiError } from '../../lib/api'
import type { Material, Movement, PageResult } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import QtyInput from '../../components/QtyInput'
import { canWrite, useAuth } from '../auth/AuthContext'

type FormValues = {
  materialId: string
  type: 'IN' | 'OUT' | 'ADJUST'
  quantity: number
  occurredAt: Dayjs
  note?: string
}

/**
 * 登记出入库（返工要求 1、3、4）。
 *
 * <p>数量带符号但用户只填正数，前端按类型补符号；盘点调整例外（盘盈为正、盘亏为负）。
 * 只读账号直连此页会得到"没有这个权限"。
 */
export default function MovementFormPage() {
  const { me } = useAuth()
  const navigate = useNavigate()
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()
  const [searchParams] = useSearchParams()
  const [type, setType] = useState<'IN' | 'OUT' | 'ADJUST'>('IN')
  const [error, setError] = useState<string | null>(null)

  // 幂等键页面加载时生成一次：重复提交、网络重试共用同一个键，服务端据此去重
  const idempotencyKey = useMemo(() => crypto.randomUUID(), [])

  const materials = useQuery({
    queryKey: ['materials', 'options'],
    queryFn: () => api.get<PageResult<Material>>('/api/v1/materials?pageSize=100'),
  })

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
        idempotencyKey,
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
      navigate('/movements')
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

  if (!canWrite(me?.role)) {
    return (
      <Result
        status="403"
        title="没有这个权限"
        subTitle="登记出入库需要录入或管理员权限；你当前是只读账号。"
      />
    )
  }

  return (
    <>
      <PageHeader title="登记出入库" description="库存以记录为准；录错了用冲正更正，记录不可删除" />

      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      <Card size="small" style={{ maxWidth: 640 }}>
        <Form
          form={form}
          layout="vertical"
          requiredMark
          initialValues={{
            type: 'IN',
            occurredAt: dayjs(),
            materialId: searchParams.get('materialId') ?? undefined,
          }}
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
            <QtyInput allowNegative={type === 'ADJUST'} />
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

          <Space>
            <Button type="primary" htmlType="submit" loading={submit.isPending}>
              {submit.isPending ? '提交中…' : '提交'}
            </Button>
            <Button onClick={() => navigate('/movements')}>取消</Button>
          </Space>

          <Typography.Paragraph
            type="secondary"
            style={{ marginTop: 16, marginBottom: 0, fontSize: 12 }}
          >
            本次提交的防重复标识：<code>{idempotencyKey.slice(0, 8)}</code>
            （网络失败重试不会重复记账）
          </Typography.Paragraph>
        </Form>
      </Card>
    </>
  )
}
