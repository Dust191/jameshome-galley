import { useState } from 'react'
import { Empty, Typography } from 'antd'
import type { TrendPoint } from '../lib/types'

/**
 * 近 30 天出入库折线。
 *
 * <p>口径复核（DES-004 M-5）：后端 SQL 只统计 `type IN ('IN','OUT') AND reversed = false`，
 * 冲正流水与其抵消的原流水都不计入——冲正不是入库，负数也不会被算成"入库笔数"。
 *
 * <p>悬浮提示用 SVG 自绘而非原生 `title`：原生 title 在触屏和平板上要长按才出、且样式不可控，
 * 现场多用平板，等于没有。这里把每一天切成一条透明竖带，指针进入或按下即显示当日数值；
 * 点击空白处收起。
 */
export default function TrendChart({ data }: { data: TrendPoint[] }) {
  const [active, setActive] = useState<number | null>(null)

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

  // 每天一条竖带，保证触屏也能点中；两端各收到绘图区边界
  const bandOf = (i: number) => {
    const left = i === 0 ? padding.left : xOf(i) - stepX / 2
    const right = i === data.length - 1 ? width - padding.right : xOf(i) + stepX / 2
    return { x: left, width: Math.max(8, right - left) }
  }

  const tooltipW = 158
  const tooltipH = 46
  const current = active == null ? null : data[active]
  let tooltipX = padding.left
  let tooltipY = padding.top
  if (active != null && current) {
    const anchorY = Math.min(yOf(current.inCount), yOf(current.outCount))
    tooltipX = Math.min(
      Math.max(xOf(active) - tooltipW / 2, padding.left),
      width - padding.right - tooltipW,
    )
    // 点靠近顶部时把提示放到点下方，避免超出画布
    tooltipY = anchorY - tooltipH - 10 < 2 ? anchorY + 12 : anchorY - tooltipH - 10
  }

  return (
    <div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="近 30 天出入库笔数趋势，轻点或悬浮查看每日数值"
        style={{ width: '100%', height: 'auto', touchAction: 'manipulation' }}
        onPointerLeave={(e) => {
          // 触屏的 pointerleave 不代表"移出"，只在鼠标场景收起
          if (e.pointerType === 'mouse') setActive(null)
        }}
        onPointerDown={(e) => {
          if (e.target === e.currentTarget) setActive(null)
        }}
      >
        {[0, 0.5, 1].map((ratio) => {
          const y = padding.top + innerH * ratio
          const value = Math.round(maxValue * (1 - ratio))
          return (
            <g key={ratio} pointerEvents="none">
              <line x1={padding.left} y1={y} x2={width - padding.right} y2={y} stroke="#f0f0f0" />
              <text x={padding.left - 8} y={y + 4} fontSize={11} fill="#999" textAnchor="end">
                {value}
              </text>
            </g>
          )
        })}

        <polyline
          points={lineOf('inCount')}
          fill="none"
          stroke="#1677ff"
          strokeWidth={2}
          pointerEvents="none"
        />
        <polyline
          points={lineOf('outCount')}
          fill="none"
          stroke="#fa8c16"
          strokeWidth={2}
          pointerEvents="none"
        />

        {data.map((d, i) => {
          const band = bandOf(i)
          return (
            <g key={d.date}>
              <rect
                x={band.x}
                y={padding.top}
                width={band.width}
                height={innerH}
                fill={active === i ? 'rgba(22,119,255,0.06)' : 'transparent'}
                style={{ cursor: 'pointer' }}
                onPointerEnter={() => setActive(i)}
                onPointerDown={() => setActive(i)}
              />
              <circle cx={xOf(i)} cy={yOf(d.inCount)} r={3} fill="#1677ff" pointerEvents="none" />
              <circle cx={xOf(i)} cy={yOf(d.outCount)} r={3} fill="#fa8c16" pointerEvents="none" />
            </g>
          )
        })}

        {current && (
          <g pointerEvents="none">
            <rect
              x={tooltipX}
              y={tooltipY}
              width={tooltipW}
              height={tooltipH}
              rx={6}
              fill="rgba(0,0,0,0.82)"
            />
            <text x={tooltipX + 10} y={tooltipY + 18} fontSize={11} fill="#fff">
              {current.date}
            </text>
            <text x={tooltipX + 10} y={tooltipY + 35} fontSize={11} fill="#fff">
              入库 {current.inCount} 笔 · 出库 {current.outCount} 笔
            </text>
          </g>
        )}

        {ticks.map((index) => (
          <text
            key={index}
            x={xOf(index)}
            y={height - 8}
            fontSize={11}
            fill="#999"
            textAnchor={index === 0 ? 'start' : index === data.length - 1 ? 'end' : 'middle'}
            pointerEvents="none"
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
          （冲正流水与其抵消的原记录不计入笔数；轻点或悬浮折线区域查看当日数值）
        </Typography.Text>
      </div>
    </div>
  )
}
