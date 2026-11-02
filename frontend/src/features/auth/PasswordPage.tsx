import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, App as AntApp, Button, Card, Form, Input, Typography } from 'antd'
import { api, ApiError } from '../../lib/api'
import { useAuth } from './AuthContext'

type FormValues = { oldPassword: string; newPassword: string; confirm: string }

/** 修改密码。改完重新拉一次 /auth/me，让"待改密"标记即时失效。 */
export default function PasswordPage() {
  const { me, refresh } = useAuth()
  const navigate = useNavigate()
  const { message } = AntApp.useApp()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onFinish = async (values: FormValues) => {
    setSubmitting(true)
    setError(null)
    try {
      await api.patch<void>('/api/v1/auth/password', {
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      })
      await refresh()
      message.success('密码已修改')
      navigate('/', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '修改失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      style={{ display: 'grid', placeItems: 'center', minHeight: '100vh', background: '#f5f5f5' }}
    >
      <Card style={{ width: 400 }}>
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          修改密码
        </Typography.Title>
        {me?.mustChangePassword && (
          <Alert
            type="warning"
            showIcon
            message="首次登录或密码被管理员重置后，需要先设置新密码才能继续使用。"
            style={{ marginBottom: 16 }}
          />
        )}
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

        <Form layout="vertical" onFinish={onFinish} requiredMark={false} disabled={submitting}>
          <Form.Item
            label="当前密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input.Password autoComplete="current-password" autoFocus />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, max: 64, message: '长度 8~64 位' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirm"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入不一致'))
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            {submitting ? '保存中…' : '保存'}
          </Button>
        </Form>
      </Card>
    </div>
  )
}
