import { useState } from 'react'
import {
  Alert,
  App as AntApp,
  Button,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Upload,
} from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import type { Category, Material, Unit } from '../../lib/types'
import QtyInput from '../../components/QtyInput'

type FormValues = {
  name: string
  categoryId: string
  unitId: string
  code?: string
  spec?: string
  safetyStock: number
  shelfLifeDays?: number | null
  note?: string
}

type UploadResponse = { key: string; url: string }

/**
 * 新建物料弹窗。
 *
 * <p>建档是"列表 → 填表 → 回列表"的短动作，独立成页会让用户丢失列表上下文，
 * 因此做成弹窗：保存成功即关闭并刷新列表。
 */
export default function MaterialCreateModal({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()
  const [imageKey, setImageKey] = useState<string | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<Category[]>('/api/v1/categories'),
  })

  const units = useQuery({
    queryKey: ['units'],
    queryFn: () => api.get<Unit[]>('/api/v1/units'),
  })

  const reset = () => {
    form.resetFields()
    setImageKey(null)
    setImageUrl(null)
    setError(null)
  }

  const save = useMutation({
    mutationFn: (values: FormValues) =>
      api.post<Material>('/api/v1/materials', {
        ...values,
        code: values.code || null,
        spec: values.spec || null,
        shelfLifeDays: values.shelfLifeDays ?? null,
        note: values.note || null,
        imageKey,
      }),
    onSuccess: () => {
      message.success('已建档')
      queryClient.invalidateQueries({ queryKey: ['materials'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      onClose()
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

  /** 先传图拿 key，保存时随表单提交（material.image_url 存 key 不存 URL）。 */
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

  return (
    <Modal
      title="新建物料"
      open={open}
      width={560}
      okText="保存"
      cancelText="取消"
      confirmLoading={save.isPending}
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
            <QtyInput allowZero addonAfter="该单位" />
          </Form.Item>
          <Form.Item label="保质期（天）" name="shelfLifeDays" style={{ flex: 1 }} tooltip="可留空">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="可留空" />
          </Form.Item>
        </Space>

        <Form.Item label="图片">
          <Space align="start">
            {imageUrl && (
              <Image
                src={imageUrl}
                alt="物料图片"
                width={72}
                height={72}
                style={{ objectFit: 'cover' }}
              />
            )}
            <Upload
              accept="image/jpeg,image/png,image/webp"
              showUploadList={false}
              customRequest={({ file, onSuccess }) =>
                uploadImage(file as File, () => onSuccess?.({}))
              }
            >
              <Button icon={<UploadOutlined />}>{imageKey ? '更换图片' : '上传图片'}</Button>
            </Upload>
          </Space>
        </Form.Item>

        <Form.Item label="备注" name="note">
          <Input.TextArea rows={2} maxLength={200} showCount />
        </Form.Item>
      </Form>
    </Modal>
  )
}
