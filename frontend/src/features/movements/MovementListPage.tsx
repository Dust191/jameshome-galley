import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntApp,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { api, ApiError, query } from '../../lib/api'
import type { Material, Movement, PageResult } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import MovementCreateModal from './MovementCreateModal'
import { MovementTypeTag } from '../../components/tags'
import { fmtQty, fmtTime, orDash } from '../../lib/format'
import { canWrite, useAuth } from '../auth/AuthContext'

export default function MovementListPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  // 登记改为弹窗：?new=1 打开；关闭即去掉参数
  const creating = searchParams.get('new') === '1'

  const [materialId, setMaterialId] = useState<string | undefined>()
  const [type, setType] = useState<string | undefined>()
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [correcting, setCorrecting] = useState<Movement | null>(null)
  const [reason, setReason] = useState('')

  const materials = useQuery({
    queryKey: ['materials', 'options'],
    queryFn: () => api.get<PageResult<Material>>('/api/v1/materials?pageSize=100'),
  })

  const list = useQuery({
    queryKey: ['movements', { materialId, type, range, page, pageSize }],
    queryFn: () =>
      api.get<PageResult<Movement>>(
        `/api/v1/movements${query({
          materialId,
          type,
          from: range?.[0]?.toISOString(),
          to: range?.[1]?.toISOString(),
          page,
          pageSize,
        })}`,
      ),
  })

  const reverse = useMutation({
    mutationFn: (payload: { id: string; note: string }) =>
      api.post(`/api/v1/movements/${payload.id}/reverse`, { note: payload.note }),
    onSuccess: () => {
      message.success('已冲正，库存已回退')
      setCorrecting(null)
      setReason('')
      queryClient.invalidateQueries({ queryKey: ['movements'] })
      queryClient.invalidateQueries({ queryKey: ['materials'] })
      queryClient.invalidateQueries({ queryKey: ['balances'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
    },
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '冲正失败')
    },
  })

  const columns = useMemo<ColumnsType<Movement>>(
    () => [
      {
        title: '发生时间',
        dataIndex: 'occurredAt',
        width: 150,
        render: (value: string) => fmtTime(value),
      },
      { title: '物料', dataIndex: 'materialName', width: 150, render: (v) => orDash(v) },
      {
        title: '类型',
        dataIndex: 'type',
        width: 100,
        render: (v) => <MovementTypeTag type={v} />,
      },
      {
        title: '数量',
        dataIndex: 'quantity',
        width: 120,
        align: 'right',
        render: (value: number) => (
          <span
            style={{
              fontVariantNumeric: 'tabular-nums',
              color: value < 0 ? '#cf1322' : '#389e0d',
              fontWeight: 600,
            }}
          >
            {value > 0 ? '+' : ''}
            {fmtQty(value)}
          </span>
        ),
      },
      { title: '单位', dataIndex: 'unitName', width: 70, render: (v) => orDash(v) },
      { title: '操作人', dataIndex: 'operatorName', width: 100, render: (v) => orDash(v) },
      { title: '备注', dataIndex: 'note', ellipsis: true, render: (v) => orDash(v) },
      {
        title: '状态',
        key: 'reversed',
        width: 100,
        render: (_, row) => {
          if (row.type === 'REVERSAL') return <Tag>冲正记录</Tag>
          return row.reversed ? <Tag color="default">已冲正</Tag> : <Tag color="green">有效</Tag>
        },
      },
      {
        title: '操作',
        key: 'action',
        width: 90,
        fixed: 'right',
        render: (_, row) => {
          const correctable = canWrite(me?.role) && row.type !== 'REVERSAL' && !row.reversed
          if (!correctable) return null
          return (
            <Button type="link" size="small" danger onClick={() => setCorrecting(row)}>
              冲正
            </Button>
          )
        },
      },
    ],
    [me?.role],
  )

  return (
    <>
      <PageHeader
        title="出入库记录"
        description="记录一经写入不可修改；录错了用「冲正」抵消"
        extra={
          canWrite(me?.role) ? (
            <Button
              type="primary"
              onClick={() => setSearchParams({ ...Object.fromEntries(searchParams), new: '1' })}
            >
              登记出入库
            </Button>
          ) : null
        }
      />

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="选择物料"
          style={{ width: 200 }}
          value={materialId}
          onChange={(value) => {
            setMaterialId(value)
            setPage(1)
          }}
          options={(materials.data?.items ?? []).map((m) => ({ label: m.name, value: m.id }))}
        />
        <Select
          allowClear
          placeholder="类型"
          style={{ width: 120 }}
          value={type}
          onChange={(value) => {
            setType(value)
            setPage(1)
          }}
          options={[
            { label: '入库', value: 'IN' },
            { label: '出库', value: 'OUT' },
            { label: '盘点调整', value: 'ADJUST' },
            { label: '冲正', value: 'REVERSAL' },
          ]}
        />
        <DatePicker.RangePicker
          showTime={{ format: 'HH:mm' }}
          value={range}
          onChange={(value) => {
            setRange(value as [Dayjs, Dayjs] | null)
            setPage(1)
          }}
        />
      </Space>

      {list.isError && (
        <Alert type="error" showIcon message="记录加载失败" style={{ marginBottom: 16 }} />
      )}

      <Table<Movement>
        rowKey="id"
        size="middle"
        loading={list.isPending}
        dataSource={list.data?.items ?? []}
        columns={columns}
        scroll={{ x: 1000 }}
        locale={{ emptyText: '还没有出入库记录' }}
        pagination={{
          current: page,
          pageSize,
          total: list.data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (total, rangeItems) =>
            `第 ${rangeItems[0]}-${rangeItems[1]} 条，共 ${total} 条`,
          onChange: (nextPage, nextSize) => {
            setPage(nextPage)
            setPageSize(nextSize)
          },
        }}
      />

      <Modal
        title="冲正这条记录"
        open={Boolean(correcting)}
        okText="确认冲正"
        cancelText="取消"
        okButtonProps={{ danger: true, loading: reverse.isPending, disabled: !reason.trim() }}
        onCancel={() => {
          setCorrecting(null)
          setReason('')
        }}
        onOk={() => {
          if (correcting) reverse.mutate({ id: correcting.id, note: reason })
        }}
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="冲正会生成一条数量相反的新记录，原记录保留并标记「已冲正」；库存随之回退。记录本身不可删除。"
        />
        {correcting && (
          <p style={{ marginBottom: 12 }}>
            {correcting.materialName} ·{' '}
            {correcting.type === 'IN' ? '入库' : correcting.type === 'OUT' ? '出库' : '盘点调整'} ·{' '}
            {fmtQty(correcting.quantity)}
          </p>
        )}
        <Form layout="vertical">
          <Form.Item label="冲正原因" required>
            <Input.TextArea
              rows={3}
              maxLength={200}
              showCount
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="例如：数量录错，需要更正"
            />
          </Form.Item>
        </Form>
      </Modal>

      <MovementCreateModal
        open={creating && canWrite(me?.role)}
        onClose={() => {
          const next = new URLSearchParams(searchParams)
          next.delete('new')
          setSearchParams(next, { replace: true })
        }}
      />
    </>
  )
}
