import { Empty, Typography } from 'antd'
import type { TrendPoint } from '../lib/types'

/**
 * 近 30 天出入库折线（返工要求 7）。
 *
 * <p>口径复核（DES-004 M-5）：后端 SQL 只统计 `type IN ('IN','OUT') AND reversed = false`，
 * 冲正流水与其抵消的原流水都不计入——冲正不是入库，负数也不会被算成"入库笔数"。
 *
 * <p>每个数据点带原生 SVG `title` 悬浮提示，不必引图表库。
 */
export default function TrendChart({ data }: { data: TrendPoint[] }) {
  if (data.length === 0) {
    return <Empty description="还没有出入库记录" />
  }

  const width = 720
  const height = 230
  const padding = { top: 16, right: 16, bottom: 28, left: 36 }
  const innerW = width - padding.left - padding.right
  const innerH = height - padding.top - padding.bottom

  const maxValue = Math.max(1, ...data.map((d) => Math.max(d.inCount, d.outCount)))
  const stepX = data.length > 1 ? innerW / (data.length - 1) : 0

  const yOf = (count: number) => padding.top + innerH - (count / maxValue) * innerH
  const xOf = (i: number) => padding.left + i * stepX

  const lineOf = (key: 'inCount' | 'outCount') =>
    data.map((d, i) => `${xOf(i)},${yOf(d[key])}`).join(' ')

  const ticks = [0, Math.floor(data.length / 2), data.length - 1]

  return (
    <div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="近 30 天出入库笔数趋势"
        style={{ width: '100%', height: 'auto' }}
      >
        {[0, 0.5, 1].map((ratio) => {
          const y = padding.top + innerH * ratio
          const value = Math.round(maxValue * (1 - ratio))
          return (
            <g key={ratio}>
              <line x1={padding.left} y1={y} x2={width - padding.right} y2={y} stroke="#f0f0f0" />
              <text x={padding.left - 8} y={y + 4} fontSize={11} fill="#999" textAnchor="end">
                {value}
              </text>
            </g>
          )
        })}

        <polyline points={lineOf('inCount')} fill="none" stroke="#1677ff" strokeWidth={2} />
        <polyline points={lineOf('outCount')} fill="none" stroke="#fa8c16" strokeWidth={2} />

        {/* 数据点：原生 title 提供悬浮数值 */}
        {data.map((d, i) => (
          <g key={d.date}>
            <circle cx={xOf(i)} cy={yOf(d.inCount)} r={3} fill="#1677ff">
              <title>{`${d.date}\n入库 ${d.inCount} 笔`}</title>
            </circle>
            <circle cx={xOf(i)} cy={yOf(d.outCount)} r={3} fill="#fa8c16">
              <title>{`${d.date}\n出库 ${d.outCount} 笔`}</title>
            </circle>
          </g>
        ))}

        {ticks.map((index) => (
          <text
            key={index}
            x={xOf(index)}
            y={height - 8}
            fontSize={11}
            fill="#999"
            textAnchor={index === 0 ? 'start' : index === data.length - 1 ? 'end' : 'middle'}
          >
            {data[index]?.date.slice(5)}
          </text>
        ))}
      </svg>

      <div style={{ display: 'flex', gap: 16, fontSize: 13, color: '#666', marginTop: 4 }}>
        <span>
          <span
            style={{
              display: 'inline-block',
              width: 10,
              height: 10,
              background: '#1677ff',
              marginRight: 6,
            }}
          />
          入库笔数
        </span>
        <span>
          <span
            style={{
              display: 'inline-block',
              width: 10,
              height: 10,
              background: '#fa8c16',
              marginRight: 6,
            }}
          />
          出库笔数
        </span>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          （冲正流水与其抵消的原记录不计入笔数；悬浮数据点可查看当日数值）
        </Typography.Text>
      </div>
    </div>
  )
}
