import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Card, Form, Input, Spin, Typography } from 'antd'
import { api, ApiError } from '../../lib/api'

type FormValues = { username: string; password: string; confirm: string }

/**
 * 注册（返工要求 2）：只填**账号**与**密码**两栏。
 *
 * <p>显示名由服务端用账号名生成，不再要求用户填写；邀请码从登录页弹窗校验后带过来，
 * 本页挂载时再校验一次——防直接敲 URL 跳过弹窗。
 */
export default function RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const code = searchParams.get('code') ?? ''

  const [error, setError] = useState<string | null>(null)
  const [fieldError, setFieldError] = useState<{ field?: string; message: string } | null>(null)

  const check = useMutation({
    mutationFn: (inviteCode: string) =>
      api.post<{ valid: boolean }>('/api/v1/invite-codes/validate', { code: inviteCode }),
  })

  // 防直接敲 URL 跳过登录页弹窗：挂载时再校验一次
  useEffect(() => {
    if (code) {
      check.mutate(code)
    }
  }, [code])

  const register = useMutation({
    mutationFn: (values: FormValues) =>
      api.post<unknown>('/api/v1/auth/register', {
        inviteCode: code,
        username: values.username,
        password: values.password,
      }),
    onSuccess: () => {
      navigate('/login', { replace: true })
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        setFieldError({ field: e.field ?? undefined, message: e.message })
      } else {
        setError('注册失败，请稍后重试')
      }
    },
  })

  if (check.isPending) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!code || check.isError || (check.isSuccess && !check.data.valid)) {
    // 校验未通过：不给表单，回到登录页重新走弹窗
    return (
      <div
        style={{ display: 'grid', placeItems: 'center', minHeight: '100vh', background: '#f5f5f5' }}
      >
        <Card style={{ width: 380 }}>
          <Alert
            type="warning"
            showIcon
            message={check.isError ? '邀请码校验失败' : '邀请码无效或已被使用'}
            description="请回到登录页重新输入邀请码。"
            style={{ marginBottom: 16 }}
          />
          <Link to="/login">
            <Button block>返回登录</Button>
          </Link>
        </Card>
      </div>
    )
  }

  return (
    <div
      style={{ display: 'grid', placeItems: 'center', minHeight: '100vh', background: '#f5f5f5' }}
    >
      <Card style={{ width: 400 }}>
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          注册新账号
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          邀请码已校验通过。注册后为只读账号，需要录入权限请联系管理员。
        </Typography.Paragraph>

        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

        <Form layout="vertical" requiredMark={false} onFinish={(values) => register.mutate(values)}>
          <Form.Item
            label="账号"
            name="username"
            validateStatus={fieldError?.field === 'username' ? 'error' : undefined}
            help={
              fieldError?.field === 'username'
                ? fieldError.message
                : '小写字母、数字与下划线，3~32 位；注册后不能用账号名以外的名字登录'
            }
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input autoComplete="username" autoFocus maxLength={32} />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            validateStatus={fieldError?.field === 'password' ? 'error' : undefined}
            help={fieldError?.field === 'password' ? fieldError.message : '长度 8~64 位'}
            rules={[
              { required: true, message: '请输入密码' },
              { min: 8, max: 64, message: '长度 8~64 位' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认密码"
            name="confirm"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入不一致'))
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={register.isPending}>
            {register.isPending ? '注册中…' : '注册'}
          </Button>
        </Form>

        <Typography.Paragraph style={{ marginTop: 16, marginBottom: 0 }} type="secondary">
          已有账号？<Link to="/login">去登录</Link>
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
