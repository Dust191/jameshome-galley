import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Descriptions,
  Form,
  Image,
  Input,
  Result,
  Select,
  Space,
  Spin,
  Typography,
  Upload,
} from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { api, ApiError } from '../../lib/api'
import type { Category, Material, Unit } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import { MaterialStatusTag, SeverityTag } from '../../components/tags'
import { fmtQty } from '../../lib/format'
import { canWrite, useAuth } from '../auth/AuthContext'
import QtyInput from '../../components/QtyInput'

type FormValues = {
  code?: string
  name: string
  categoryId: string
  unitId: string
  spec?: string
  safetyStock: number
  shelfLifeDays?: number | null
  note?: string
}

type UploadResponse = { key: string; url: string }

/**
 * 物料详情 / 新建 / 编辑（返工要求 3、4、5）。
 *
 * <p>只读账号进详情页看到的是纯展示（无保存、无上传）；直连 `/materials/new` 会得到
 * "没有这个权限"，不再出现可点击的完整表单。
 */
export default function MaterialFormPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = Boolean(id)
  const { me } = useAuth()
  const navigate = useNavigate()
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()

  const [imageKey, setImageKey] = useState<string | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const editable = canWrite(me?.role)
  // 只读账号新建物料：直接拒绝，不渲染表单
  if (!editable && !isEdit) {
    return <Result status="403" title="没有这个权限" subTitle="新建物料需要录入或管理员权限。" />
  }

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<Category[]>('/api/v1/categories'),
  })

  const units = useQuery({
    queryKey: ['units'],
    queryFn: () => api.get<Unit[]>('/api/v1/units'),
  })

  const detail = useQuery({
    queryKey: ['material', id],
    queryFn: () => api.get<Material>(`/api/v1/materials/${id}`),
    enabled: isEdit,
  })

  useEffect(() => {
    if (!detail.data) return
    const m = detail.data
    form.setFieldsValue({
      code: m.code ?? undefined,
      name: m.name,
      categoryId: m.categoryId,
      unitId: m.unitId,
      spec: m.spec ?? undefined,
      safetyStock: m.safetyStock,
      shelfLifeDays: m.shelfLifeDays ?? undefined,
      note: m.note ?? undefined,
    })
    setImageKey(m.imageKey)
    setImageUrl(m.imageUrl)
  }, [detail.data, form])

  const save = useMutation({
    mutationFn: async (values: FormValues) => {
      const payload = {
        ...values,
        code: values.code || null,
        spec: values.spec || null,
        shelfLifeDays: values.shelfLifeDays ?? null,
        note: values.note || null,
        imageKey,
      }
      if (isEdit) {
        return api.patch<Material>(`/api/v1/materials/${id}`, {
          ...payload,
          version: detail.data?.version,
        })
      }
      return api.post<Material>('/api/v1/materials', payload)
    },
    onSuccess: () => {
      message.success(isEdit ? '已保存' : '已建档')
      queryClient.invalidateQueries({ queryKey: ['materials'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      navigate('/materials')
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        if (e.field) {
          form.setFields([{ name: e.field as keyof FormValues, errors: [e.message] }])
        } else {
          setError(e.message)
        }
      } else {
        setError('保存失败，请稍后重试')
      }
    },
  })

  // ---------- 只读视图（返工要求 3） ----------
  if (!editable) {
    if (detail.isPending) return <Spin />
    if (detail.isError || !detail.data) {
      return <Alert type="error" showIcon message="物料不存在或已删除" />
    }
    const m = detail.data
    return (
      <>
        <PageHeader title={m.name} description="只读账号仅可查看" />
        <Card size="small" style={{ maxWidth: 720 }}>
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="自编码">{m.code ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <MaterialStatusTag status={m.status} />
            </Descriptions.Item>
            <Descriptions.Item label="分类">{m.categoryName}</Descriptions.Item>
            <Descriptions.Item label="单位">{m.unitName}</Descriptions.Item>
            <Descriptions.Item label="规格">{m.spec ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="安全库存">{fmtQty(m.safetyStock)}</Descriptions.Item>
            <Descriptions.Item label="当前结存">
              <Space>
                <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>
                  {fmtQty(m.currentStock)}
                </span>
                {(() => {
                  const qty = m.currentStock
                  if (qty < 0) return <SeverityTag severity={3} reason="负库存" />
                  if (qty === 0) return <SeverityTag severity={2} reason="缺货" />
                  if (m.safetyStock > 0 && qty < m.safetyStock)
                    return <SeverityTag severity={1} reason="待补货" />
                  return null
                })()}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="保质期">
              {m.shelfLifeDays ? `${m.shelfLifeDays} 天` : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="图片" span={2}>
              {m.imageUrl ? (
                <Image src={m.imageUrl} alt={m.name} width={120} style={{ objectFit: 'cover' }} />
              ) : (
                '—'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {m.note ?? '—'}
            </Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 16 }}>
            <Button onClick={() => navigate('/materials')}>返回列表</Button>
          </div>
        </Card>
      </>
    )
  }

  // ---------- 编辑/新建表单 ----------
  /** 走 customRequest：完全接管上传流程，不依赖 antd 的默认行为（返工要求 5）。 */
  const uploadImage = (file: File, onDone: () => void) => {
    const data = new FormData()
    data.append('file', file)
    api
      .upload<UploadResponse>('/api/v1/files', data)
      .then((result) => {
        setImageKey(result.key)
        setImageUrl(result.url)
        message.success('图片已上传')
      })
      .catch((e) => {
        message.error(e instanceof ApiError ? e.message : '上传失败')
      })
      .finally(onDone)
  }

  if (isEdit && detail.isPending) return <Spin />

  return (
    <>
      <PageHeader title={isEdit ? '编辑物料' : '新建物料'} />

      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      <Card size="small" style={{ maxWidth: 720 }}>
        <Form
          form={form}
          layout="vertical"
          requiredMark
          initialValues={{ safetyStock: 0 }}
          onFinish={(values) => save.mutate(values)}
          disabled={save.isPending}
        >
          <Form.Item
            label="物料名"
            name="name"
            rules={[{ required: true, message: '物料名不能为空' }]}
          >
            <Input maxLength={80} autoFocus />
          </Form.Item>

          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              label="分类"
              name="categoryId"
              rules={[{ required: true, message: '请选择分类' }]}
              style={{ flex: 1 }}
            >
              <Select
                placeholder="选择分类"
                options={(categories.data ?? []).map((c) => ({ label: c.name, value: c.id }))}
              />
            </Form.Item>
            <Form.Item
              label="单位"
              name="unitId"
              rules={[{ required: true, message: '请选择单位' }]}
              style={{ flex: 1 }}
            >
              <Select
                placeholder="选择单位"
                options={(units.data ?? []).map((u) => ({ label: u.name, value: u.id }))}
              />
            </Form.Item>
          </Space>

          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              label="自编码"
              name="code"
              style={{ flex: 1 }}
              rules={[{ max: 32, message: '不超过 32 字' }]}
            >
              <Input placeholder="可留空" />
            </Form.Item>
            <Form.Item label="规格" name="spec" style={{ flex: 1 }}>
              <Input placeholder="如 25kg/袋" maxLength={50} />
            </Form.Item>
          </Space>

          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              label="安全库存"
              name="safetyStock"
              style={{ flex: 1 }}
              tooltip="低于该数量会进入「待补货」清单；填 0 表示不设预警线"
              rules={[{ required: true, message: '安全库存不能为空' }]}
            >
              {/* 不用 InputNumber 的 min/precision：避免失焦静默钳位（返工要求 4） */}
              <QtyInput />
            </Form.Item>
            <Form.Item
              label="保质期（天）"
              name="shelfLifeDays"
              style={{ flex: 1 }}
              tooltip="可留空"
              rules={[
                () => ({
                  validator(_, value) {
                    if (value == null || (Number.isInteger(value) && value > 0)) {
                      return Promise.resolve()
                    }
                    return Promise.reject(new Error('须为大于 0 的整数'))
                  },
                }),
              ]}
            >
              <Input inputMode="numeric" placeholder="可留空" />
            </Form.Item>
          </Space>

          <Form.Item label="图片">
            <Space align="start">
              {imageUrl && (
                <Image
                  src={imageUrl}
                  alt="物料图片"
                  width={96}
                  height={96}
                  style={{ objectFit: 'cover' }}
                />
              )}
              <Upload
                accept="image/jpeg,image/png,image/webp"
                showUploadList={false}
                customRequest={({ file, onSuccess }) => {
                  uploadImage(file as File, () => onSuccess?.({}))
                }}
              >
                <Button icon={<UploadOutlined />}>{imageKey ? '更换图片' : '上传图片'}</Button>
              </Upload>
              {imageKey && (
                <Button
                  type="link"
                  danger
                  onClick={() => {
                    setImageKey(null)
                    setImageUrl(null)
                  }}
                >
                  移除
                </Button>
              )}
            </Space>
          </Form.Item>

          <Form.Item label="备注" name="note">
            <Input.TextArea rows={2} maxLength={200} showCount />
          </Form.Item>

          <Space>
            <Button type="primary" htmlType="submit" loading={save.isPending}>
              {save.isPending ? '保存中…' : '保存'}
            </Button>
            <Button onClick={() => navigate('/materials')}>取消</Button>
          </Space>

          <Typography.Paragraph
            type="secondary"
            style={{ marginTop: 12, marginBottom: 0, fontSize: 12 }}
          >
            数量统一保留三位小数；填错时系统会提示而不是悄悄改正。
          </Typography.Paragraph>
        </Form>
      </Card>
    </>
  )
}
