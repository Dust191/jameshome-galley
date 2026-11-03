import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Card, Form, Input, Modal, Typography } from 'antd'
import { api, ApiError } from '../../lib/api'
import { useAuth } from './AuthContext'

type FormValues = { username: string; password: string }

/**
 * 登录。
 *
 * <p>注册入口在这里弹出邀请码校验（返工要求 2）：先验码再进表单，
 * 避免受邀者填完账号密码才被告知码不对。校验只回布尔值，不区分无效原因（D-18）。
 */
export default function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [inviteCode, setInviteCode] = useState('')
  const [inviteError, setInviteError] = useState<string | null>(null)

  const checkInvite = useMutation({
    mutationFn: (code: string) =>
      api.post<{ valid: boolean }>('/api/v1/invite-codes/validate', { code }),
    onSuccess: (data) => {
      if (data.valid) {
        setRegisterOpen(false)
        navigate(`/register?code=${encodeURIComponent(inviteCode.trim())}`)
      } else {
        setInviteError('邀请码无效或已被使用')
      }
    },
    onError: () => {
      setInviteError('校验失败，请稍后重试')
    },
  })

  const onFinish = async (values: FormValues) => {
    setError(null)
    setSubmitting(true)
    try {
      const me = await signIn(values.username, values.password)
      navigate(me.mustChangePassword ? '/password' : '/', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      style={{ display: 'grid', placeItems: 'center', minHeight: '100vh', background: '#f5f5f5' }}
    >
      <Card style={{ width: 360 }}>
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          登录
        </Typography.Title>
        <Typography.Paragraph type="secondary">团体厨房库存远程查看系统</Typography.Paragraph>

        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            label="账号"
            name="username"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            {submitting ? '登录中…' : '登录'}
          </Button>
        </Form>

        <Typography.Paragraph style={{ marginTop: 16, marginBottom: 0 }} type="secondary">
          收到邀请码？
          <Button
            type="link"
            size="small"
            style={{ paddingInline: 4 }}
            onClick={() => {
              setRegisterOpen(true)
              setInviteError(null)
            }}
          >
            注册新账号
          </Button>
        </Typography.Paragraph>
      </Card>

      <Modal
        title="输入邀请码"
        centered
        open={registerOpen}
        okText="校验并继续"
        cancelText="取消"
        okButtonProps={{ disabled: !inviteCode.trim(), loading: checkInvite.isPending }}
        onCancel={() => {
          setRegisterOpen(false)
          setInviteError(null)
        }}
        onOk={() => checkInvite.mutate(inviteCode.trim())}
      >
        <Typography.Paragraph type="secondary">
          邀请码由管理员发放。校验通过后进入注册页，设置账号与密码。
        </Typography.Paragraph>
        <Input
          autoFocus
          value={inviteCode}
          onChange={(e) => {
            setInviteCode(e.target.value)
            setInviteError(null)
          }}
          placeholder="例如：4FGHTARU"
          onPressEnter={() => inviteCode.trim() && checkInvite.mutate(inviteCode.trim())}
        />
        {inviteError && (
          <Alert type="error" showIcon message={inviteError} style={{ marginTop: 12 }} />
        )}
      </Modal>
    </div>
  )
}
