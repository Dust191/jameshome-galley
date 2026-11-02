import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntApp, Button, Space, Table, Typography } from 'antd'
import { api, ApiError } from '../../lib/api'
import type { InviteView } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import { InviteStatusTag } from '../../components/tags'
import { fmtTime } from '../../lib/format'

/** 邀请码管理（仅管理员）。新账号只能凭邀请码注册。 */
export default function InvitesPage() {
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [justCreated, setJustCreated] = useState<string | null>(null)

  const list = useQuery({
    queryKey: ['invites'],
    queryFn: () => api.get<InviteView[]>('/api/v1/invite-codes'),
  })

  const create = useMutation({
    mutationFn: () => api.post<InviteView>('/api/v1/invite-codes'),
    onSuccess: (data) => {
      setJustCreated(data.code)
      queryClient.invalidateQueries({ queryKey: ['invites'] })
    },
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '生成失败')
    },
  })

  const disable = useMutation({
    mutationFn: (id: string) => api.patch<InviteView>(`/api/v1/invite-codes/${id}`),
    onSuccess: () => {
      message.success('已禁用')
      queryClient.invalidateQueries({ queryKey: ['invites'] })
    },
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '禁用失败')
    },
  })

  return (
    <>
      <PageHeader
        title="邀请码"
        description="新账号只能凭邀请码注册，注册后默认为只读"
        extra={
          <Button type="primary" loading={create.isPending} onClick={() => create.mutate()}>
            生成邀请码
          </Button>
        }
      />

      {justCreated && (
        <Alert
          type="success"
          showIcon
          closable
          style={{ marginBottom: 16 }}
          onClose={() => setJustCreated(null)}
          message={
            <span>
              新邀请码：
              <Typography.Text copyable strong>
                {justCreated}
              </Typography.Text>
            </span>
          }
        />
      )}

      {list.isError && (
        <Alert type="error" showIcon message="邀请码加载失败" style={{ marginBottom: 16 }} />
      )}

      <Table<InviteView>
        rowKey="id"
        size="middle"
        loading={list.isPending}
        dataSource={list.data ?? []}
        pagination={false}
        locale={{ emptyText: '还没有邀请码，先生成一个' }}
        columns={[
          {
            title: '邀请码',
            dataIndex: 'code',
            width: 160,
            render: (code: string) => <Typography.Text copyable>{code}</Typography.Text>,
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (status) => <InviteStatusTag status={status} />,
          },
          {
            title: '使用时间',
            dataIndex: 'usedAt',
            width: 160,
            render: (value: string | null) => fmtTime(value),
          },
          {
            title: '操作',
            key: 'action',
            render: (_, row) => {
              if (row.status !== 'unused') return null
              return (
                <Space>
                  <Button
                    type="link"
                    size="small"
                    danger
                    loading={disable.isPending}
                    onClick={() => disable.mutate(row.id)}
                  >
                    禁用
                  </Button>
                </Space>
              )
            },
          },
        ]}
      />

      <Typography.Paragraph type="secondary" style={{ marginTop: 12, fontSize: 12 }}>
        提示：邀请码只显示"未使用 / 已使用 / 已禁用"三种状态；被禁用或已使用的码对外表现一致，
        避免被用来试探哪些码还有效。
      </Typography.Paragraph>
    </>
  )
}
