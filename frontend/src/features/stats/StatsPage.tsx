import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Radio, Skeleton, Table } from 'antd'
import { api } from '../../lib/api'
import type { CategoryShare, TopOutItem } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import CategoryBar from '../../components/CategoryBar'
import { fmtQty } from '../../lib/format'

/** 统计详情（仅管理员，DES-003 §2）。图表提供"查看数据表"切换。 */
export default function StatsPage() {
  const [categoryView, setCategoryView] = useState<'chart' | 'table'>('chart')

  const categories = useQuery({
    queryKey: ['stats', 'category-distribution'],
    queryFn: () => api.get<CategoryShare[]>('/api/v1/stats/category-distribution'),
  })

  const topOut = useQuery({
    queryKey: ['stats', 'top-out'],
    queryFn: () => api.get<TopOutItem[]>('/api/v1/stats/top-out?limit=10'),
  })

  return (
    <>
      <PageHeader title="统计" description="数量不含跨单位合计；口径见 DES-004" />

      <Card
        title="分类分布"
        size="small"
        extra={
          <Radio.Group
            size="small"
            value={categoryView}
            onChange={(e) => setCategoryView(e.target.value)}
            options={[
              { label: '图表', value: 'chart' },
              { label: '数据表', value: 'table' },
            ]}
          />
        }
      >
        {categories.isPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : categories.isError ? (
          <Alert type="error" showIcon message="分类分布加载失败" />
        ) : categoryView === 'chart' ? (
          <CategoryBar data={categories.data} />
        ) : (
          <Table
            size="small"
            rowKey="categoryName"
            dataSource={categories.data}
            pagination={false}
            columns={[
              { title: '分类', dataIndex: 'categoryName' },
              { title: '种类数', dataIndex: 'materialCount', align: 'right', width: 100 },
              {
                title: '占比',
                dataIndex: 'ratio',
                align: 'right',
                width: 100,
                render: (value: string) => `${(Number(value) * 100).toFixed(1)}%`,
              },
            ]}
          />
        )}
      </Card>

      <Card title="出库频次 Top10（近 30 天）" size="small" style={{ marginTop: 16 }}>
        {topOut.isPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : topOut.isError ? (
          <Alert type="error" showIcon message="Top10 加载失败" />
        ) : (
          <Table
            size="small"
            rowKey="materialName"
            dataSource={topOut.data}
            pagination={false}
            locale={{ emptyText: '近 30 天没有出库记录' }}
            columns={[
              { title: '物料', dataIndex: 'materialName' },
              { title: '单位', dataIndex: 'unitName', width: 80 },
              { title: '出库笔数', dataIndex: 'outCount', align: 'right', width: 110 },
              {
                title: '净出库量',
                dataIndex: 'totalQuantity',
                align: 'right',
                width: 130,
                render: (value: number) => (
                  <span style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtQty(value)}</span>
                ),
              },
            ]}
          />
        )}
      </Card>
    </>
  )
}
