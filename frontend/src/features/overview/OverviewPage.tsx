import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Col, Row, Skeleton, Statistic, Table } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../../lib/api'
import type { AlertList, Overview, TrendPoint } from '../../lib/types'
import PageHeader from '../../components/PageHeader'
import TrendChart from '../../components/TrendChart'
import { SeverityTag } from '../../components/tags'

/**
 * 概览（返工要求 7）：低库存 / 缺货 / 长期未动卡片可点击，
 * 跳转到物料列表并自动带上对应筛选；预警卡"去处理"落地即筛出全部待处理物料。
 */
export default function OverviewPage() {
  const navigate = useNavigate()

  const overview = useQuery({
    queryKey: ['stats', 'overview'],
    queryFn: () => api.get<Overview>('/api/v1/stats/overview'),
  })

  const trend = useQuery({
    queryKey: ['stats', 'trend'],
    queryFn: () => api.get<TrendPoint[]>('/api/v1/stats/trend'),
  })

  const alerts = useQuery({
    queryKey: ['stats', 'alerts', 5],
    queryFn: () => api.get<AlertList>('/api/v1/stats/alerts?page=1&pageSize=5'),
  })

  const statCard = (
    title: string,
    value: number | undefined,
    color: string | undefined,
    target?: string,
  ) => {
    const clickable = Boolean(target)
    return (
      <Card
        size="small"
        hoverable={clickable}
        onClick={target ? () => navigate(target) : undefined}
        style={clickable ? { cursor: 'pointer' } : undefined}
      >
        <Statistic
          title={
            clickable ? (
              <span>
                {title}
                <Link
                  to={target ?? '#'}
                  onClick={(e) => e.stopPropagation()}
                  style={{ fontSize: 12, marginLeft: 6 }}
                >
                  查看
                </Link>
              </span>
            ) : (
              title
            )
          }
          value={value ?? 0}
          suffix="种"
          valueStyle={{ color: value && value > 0 ? color : undefined }}
        />
      </Card>
    )
  }

  return (
    <>
      <PageHeader title="概览" description="点击数字卡可跳到对应清单" />

      {overview.isPending ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : overview.isError ? (
        <Alert type="error" showIcon message="总览加载失败" />
      ) : (
        <Row gutter={12}>
          <Col xs={12} md={8} lg={4}>
            {statCard('在管物料', overview.data.materialCount, undefined)}
          </Col>
          <Col xs={12} md={8} lg={5}>
            {statCard(
              '待补货',
              overview.data.lowStockCount,
              '#fa8c16',
              '/materials?stockState=low',
            )}
          </Col>
          <Col xs={12} md={8} lg={5}>
            {statCard(
              '缺货',
              overview.data.zeroStockCount,
              '#cf1322',
              '/materials?stockState=zero',
            )}
          </Col>
          <Col xs={12} md={8} lg={5}>
            {statCard(
              '长期未动',
              overview.data.staleCount,
              undefined,
              '/materials?stockState=stale',
            )}
          </Col>
          <Col xs={12} md={8} lg={5}>
            <Card size="small">
              <Statistic
                title="资料完整率"
                value={(Number(overview.data.completenessRate) * 100).toFixed(1)}
                suffix="%"
              />
            </Card>
          </Col>
        </Row>
      )}

      <Card
        title="近 30 天出入库"
        size="small"
        style={{ marginTop: 16 }}
        extra={<Link to="/movements">查看流水</Link>}
      >
        {trend.isPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : trend.isError ? (
          <Alert type="error" showIcon message="趋势加载失败" />
        ) : (
          <TrendChart data={trend.data} />
        )}
      </Card>

      <Card
        title="需要处理（前 5 条）"
        size="small"
        style={{ marginTop: 16 }}
        extra={<Link to="/materials?stockState=alert">全部待处理</Link>}
      >
        {alerts.isPending ? (
          <Skeleton active paragraph={{ rows: 3 }} />
        ) : (
          <Table
            size="small"
            rowKey={(row) => `${row.materialName}-${row.severity}`}
            dataSource={alerts.data?.items ?? []}
            pagination={false}
            locale={{ emptyText: '没有需要处理的预警' }}
            columns={[
              { title: '物料', dataIndex: 'materialName' },
              { title: '单位', dataIndex: 'unitName', width: 80 },
              {
                title: '当前结存',
                dataIndex: 'quantity',
                align: 'right',
                width: 120,
                render: (value: string) => (
                  <span style={{ fontVariantNumeric: 'tabular-nums' }}>{value}</span>
                ),
              },
              {
                title: '安全库存',
                dataIndex: 'safetyStock',
                align: 'right',
                width: 120,
                render: (value: string) => (
                  <span style={{ fontVariantNumeric: 'tabular-nums' }}>{value}</span>
                ),
              },
              {
                title: '状态',
                key: 'severity',
                width: 140,
                render: (_, row) => <SeverityTag severity={row.severity} reason={row.reason} />,
              },
            ]}
          />
        )}
        {alerts.data && alerts.data.total > 5 && (
          <div style={{ marginTop: 8, fontSize: 13, color: '#999' }}>
            共 {alerts.data.total} 条待处理，此处仅显示前 5 条。
          </div>
        )}
      </Card>
    </>
  )
}
