import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntApp, Button, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import { api, ApiError } from '../../lib/api'
import type { Role, UserView } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import { AccountStatusTag } from '../../components/tags'
import { fmtTime, ROLE_LABEL } from '../../lib/format'
import { useAuth } from '../auth/AuthContext'

/**
 * 账号管理（仅管理员）。
 *
 * <p>重置密码后服务端返回一次性密码并置"须改密"，这里把它显示一次——
 * 不显示等于管理员拿不到密码，只能再重置一遍。
 */
export default function UsersPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [temporary, setTemporary] = useState<{ username: string; password: string } | null>(null)

  const list = useQuery({
    queryKey: ['users'],
    queryFn: () => api.get<UserView[]>('/api/v1/users'),
  })

  const update = useMutation({
    mutationFn: (payload: { id: string; role?: Role; status?: 'active' | 'disabled' }) =>
      api.patch(`/api/v1/users/${payload.id}`, { role: payload.role, status: payload.status }),
    onSuccess: () => {
      message.success('已更新')
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '更新失败')
    },
  })

  const resetPassword = useMutation({
    mutationFn: (user: UserView) =>
      api.post<{ temporaryPassword: string }>(`/api/v1/users/${user.id}/reset-password`),
    onSuccess: (data, user) => {
      setTemporary({ username: user.username, password: data.temporaryPassword })
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '重置失败')
    },
  })

  return (
    <>
      <PageHeader title="账号" description="角色决定能做什么：只读 / 录入 / 管理员" />

      {list.isError && (
        <Alert type="error" showIcon message="账号列表加载失败" style={{ marginBottom: 16 }} />
      )}

      <Table<UserView>
        rowKey="id"
        size="middle"
        loading={list.isPending}
        dataSource={list.data ?? []}
        pagination={false}
        columns={[
          { title: '账号', dataIndex: 'username', width: 140 },
          { title: '显示名', dataIndex: 'displayName', width: 140 },
          {
            title: '角色',
            dataIndex: 'role',
            width: 160,
            render: (role: Role, row) => (
              <Select<Role>
                size="small"
                value={role}
                style={{ width: 120 }}
                disabled={row.id === me?.id}
                onChange={(value) => update.mutate({ id: row.id, role: value })}
                options={(Object.keys(ROLE_LABEL) as Role[]).map((r) => ({
                  label: ROLE_LABEL[r],
                  value: r,
                }))}
              />
            ),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (status) => <AccountStatusTag status={status} />,
          },
          {
            title: '密码更新',
            dataIndex: 'passwordUpdatedAt',
            width: 160,
            render: (value: string | null) => fmtTime(value),
          },
          {
            title: '操作',
            key: 'action',
            render: (_, row) => {
              const isSelf = row.id === me?.id
              return (
                <Space size="small">
                  <Button
                    type="link"
                    size="small"
                    disabled={isSelf}
                    onClick={() =>
                      update.mutate({
                        id: row.id,
                        status: row.status === 'active' ? 'disabled' : 'active',
                      })
                    }
                  >
                    {row.status === 'active' ? '停用' : '启用'}
                  </Button>
                  <Button type="link" size="small" onClick={() => resetPassword.mutate(row)}>
                    重置密码
                  </Button>
                  {isSelf && <Tag>当前账号</Tag>}
                </Space>
              )
            },
          },
        ]}
      />

      <Modal
        title="一次性密码"
        open={Boolean(temporary)}
        onCancel={() => setTemporary(null)}
        footer={
          <Button type="primary" onClick={() => setTemporary(null)}>
            我已记录
          </Button>
        }
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="该密码只显示这一次。对方下次登录时需要自行设置新密码。"
        />
        <Typography.Paragraph>
          账号：<code>{temporary?.username}</code>
        </Typography.Paragraph>
        <Typography.Paragraph copyable style={{ fontSize: 18, fontWeight: 600 }}>
          {temporary?.password}
        </Typography.Paragraph>
      </Modal>
    </>
  )
}
