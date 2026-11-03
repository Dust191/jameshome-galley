import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntApp,
  Button,
  Image,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DownloadOutlined } from '@ant-design/icons'
import { api, ApiError, downloadFile, query } from '../../lib/api'
import type { Category, Material, PageResult } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import { MaterialStatusTag, SeverityTag } from '../../components/tags'
import { fmtQty, orDash, STOCK_STATE_OPTIONS } from '../../lib/format'
import { canWrite, isAdmin, useAuth } from '../auth/AuthContext'

/** 结存徽标：只在异常时显示，正常不渲染，避免整列花绿。 */
function stockBadge(material: Material) {
  const qty = material.currentStock
  if (qty < 0) return <SeverityTag severity={3} reason="负库存" />
  if (qty === 0) return <SeverityTag severity={2} reason="缺货" />
  if (material.safetyStock > 0 && qty < material.safetyStock)
    return <SeverityTag severity={1} reason="待补货" />
  return null
}

export default function MaterialListPage() {
  const { me } = useAuth()
  const navigate = useNavigate()
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  // 概览卡跳转带 ?stockState=xxx：初始化进筛选条件，落地即筛好
  const initialStockState = searchParams.get('stockState') ?? undefined

  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<string | undefined>()
  const [stockState, setStockState] = useState<string | undefined>(initialStockState)
  const [status, setStatus] = useState<string>('active')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [sort, setSort] = useState('-updatedAt')

  // URL 参数变化（例如再次点击概览卡）时同步筛选
  useEffect(() => {
    setStockState(searchParams.get('stockState') ?? undefined)
    setPage(1)
  }, [searchParams])

  // 输入 400ms 防抖（DES-003 §3）
  useEffect(() => {
    const timer = setTimeout(() => {
      setKeyword(keywordInput)
      setPage(1)
    }, 400)
    return () => clearTimeout(timer)
  }, [keywordInput])

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<Category[]>('/api/v1/categories'),
  })

  const list = useQuery({
    queryKey: ['materials', { keyword, categoryId, stockState, status, page, pageSize, sort }],
    queryFn: () =>
      api.get<PageResult<Material>>(
        `/api/v1/materials${query({ keyword, categoryId, stockState, status, sort, page, pageSize })}`,
      ),
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/v1/materials/${id}`),
    onSuccess: () => {
      message.success('已删除')
      queryClient.invalidateQueries({ queryKey: ['materials'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
    },
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '删除失败')
    },
  })

  const toggleStatus = useMutation({
    mutationFn: (material: Material) =>
      api.patch(`/api/v1/materials/${material.id}`, {
        status: material.status === 'active' ? 'inactive' : 'active',
        version: material.version,
      }),
    onSuccess: () => {
      message.success('状态已更新')
      queryClient.invalidateQueries({ queryKey: ['materials'] })
    },
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '更新失败')
    },
  })

  const stockStateSelect = useMemo(
    () =>
      STOCK_STATE_OPTIONS.filter((o) => o.value !== 'alert').map((o) => ({
        label: o.label,
        value: o.value,
      })),
    [],
  )

  /** 导出当前筛选结果，而不是"全部物料"——用户看到什么就导出什么。 */
  const exportCsv = useMutation({
    mutationFn: () =>
      downloadFile(
        `/api/v1/exports/materials.csv${query({ keyword, categoryId, stockState, status })}`,
      ),
    onError: (error) => {
      message.error(error instanceof ApiError ? error.message : '导出失败，请稍后重试')
    },
  })

  const columns = useMemo<ColumnsType<Material>>(
    () => [
      {
        title: '物料名',
        dataIndex: 'name',
        fixed: 'left',
        width: 170,
        render: (name: string, row) => (
          <Space direction="vertical" size={0} style={{ maxWidth: '100%' }}>
            {/* 长名称在 170px 列宽内换行，不撑破表格也不截断 */}
            <Link
              to={`/materials/${row.id}`}
              style={{ display: 'block', wordBreak: 'break-word', overflowWrap: 'anywhere' }}
            >
              {name}
            </Link>
            {stockBadge(row)}
          </Space>
        ),
      },
      {
        // 批量选料时靠缩略图确认，不必逐个点开详情
        title: '图片',
        dataIndex: 'imageUrl',
        width: 68,
        align: 'center',
        render: (url: string | null, row) =>
          url ? (
            <Image
              src={url}
              alt={row.name}
              width={40}
              height={40}
              style={{ objectFit: 'cover', borderRadius: 4 }}
            />
          ) : (
            <span style={{ color: '#c8c8c8' }}>—</span>
          ),
      },
      { title: '自编码', dataIndex: 'code', width: 100, render: (v) => orDash(v) },
      { title: '分类', dataIndex: 'categoryName', width: 100 },
      { title: '规格', dataIndex: 'spec', width: 150, render: (v) => orDash(v) },
      { title: '单位', dataIndex: 'unitName', width: 70 },
      {
        title: '安全库存',
        dataIndex: 'safetyStock',
        width: 110,
        align: 'right',
        sorter: true,
        render: (v: number) => (
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtQty(v)}</span>
        ),
      },
      {
        title: '当前结存',
        dataIndex: 'currentStock',
        width: 120,
        align: 'right',
        sorter: true,
        render: (v: number) => (
          <span
            style={{
              fontVariantNumeric: 'tabular-nums',
              color: v < 0 ? '#cf1322' : undefined,
              fontWeight: v < 0 ? 600 : undefined,
            }}
          >
            {fmtQty(v)}
          </span>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (v) => <MaterialStatusTag status={v} />,
      },
      {
        title: '操作',
        key: 'action',
        width: 150,
        fixed: 'right',
        render: (_, row) => {
          if (!canWrite(me?.role)) return null
          return (
            <Space size="small">
              <Button type="link" size="small" onClick={() => navigate(`/materials/${row.id}`)}>
                编辑
              </Button>
              <Tooltip title={row.status === 'archived' ? '已归档不可再改' : undefined}>
                <Button
                  type="link"
                  size="small"
                  disabled={row.status === 'archived'}
                  onClick={() => toggleStatus.mutate(row)}
                >
                  {row.status === 'active' ? '停用' : '启用'}
                </Button>
              </Tooltip>
              {isAdmin(me?.role) && (
                <Popconfirm
                  title="删除物料"
                  description="已有出入库记录的物料不能删除，可改为停用。"
                  okText="删除"
                  cancelText="取消"
                  onConfirm={() => remove.mutate(row.id)}
                >
                  <Button type="link" size="small" danger>
                    删除
                  </Button>
                </Popconfirm>
              )}
            </Space>
          )
        },
      },
    ],
    [me?.role, navigate, remove, toggleStatus],
  )

  return (
    <>
      <PageHeader
        title="物料"
        description={`共 ${list.data?.total ?? 0} 种`}
        extra={
          <>
            <Button
              icon={<DownloadOutlined />}
              loading={exportCsv.isPending}
              onClick={() => exportCsv.mutate()}
            >
              导出
            </Button>
            {canWrite(me?.role) && (
              <Button type="primary" onClick={() => navigate('/materials/new')}>
                新建物料
              </Button>
            )}
          </>
        }
      />

      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="按名称或自编码搜索"
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          style={{ width: 240 }}
        />
        <Select
          allowClear
          placeholder="分类"
          style={{ width: 140 }}
          value={categoryId}
          onChange={(value) => {
            setCategoryId(value)
            setPage(1)
          }}
          options={(categories.data ?? []).map((c) => ({ label: c.name, value: c.id }))}
        />
        <Select
          allowClear
          placeholder="库存状态"
          style={{ width: 200 }}
          value={stockState}
          onChange={(value) => {
            setStockState(value)
            setPage(1)
            setSearchParams(value ? { stockState: value } : {}, { replace: true })
          }}
          options={stockStateSelect}
        />
        <Select
          style={{ width: 140 }}
          value={status}
          onChange={(value) => {
            setStatus(value)
            setPage(1)
          }}
          options={[
            { label: '仅启用', value: 'active' },
            { label: '仅停用', value: 'inactive' },
            { label: '仅归档', value: 'archived' },
            { label: '全部', value: 'all' },
          ]}
        />
      </Space>

      {list.isError && (
        <Alert type="error" showIcon message="物料列表加载失败" style={{ marginBottom: 16 }} />
      )}

      <Table<Material>
        rowKey="id"
        size="middle"
        loading={list.isPending}
        dataSource={list.data?.items ?? []}
        columns={columns}
        scroll={{ x: 1150 }}
        locale={{ emptyText: canWrite(me?.role) ? '还没有物料，先建一条' : '没有符合条件的物料' }}
        pagination={{
          current: page,
          pageSize,
          total: list.data?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
          onChange: (nextPage, nextSize) => {
            setPage(nextPage)
            setPageSize(nextSize)
          },
        }}
        onChange={(_pagination, _filters, sorter) => {
          const single = Array.isArray(sorter) ? sorter[0] : sorter
          if (!single?.order) {
            setSort('-updatedAt')
            return
          }
          const field = single.field === 'currentStock' ? 'currentStock' : 'safetyStock'
          setSort(single.order === 'descend' ? `-${field}` : field)
        }}
      />
    </>
  )
}
