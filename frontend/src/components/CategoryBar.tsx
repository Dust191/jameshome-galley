import { Empty } from 'antd'
import type { CategoryShare } from '../lib/types'

/** 分类分布横向条形。刻意不用饼图：类别多时扇形角度肉眼分不清，条形按长度直接比。 */
export default function CategoryBar({ data }: { data: CategoryShare[] }) {
  if (data.length === 0) {
    return <Empty description="还没有物料数据" />
  }

  const max = Math.max(...data.map((d) => d.materialCount), 1)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {data.map((item) => (
        <div key={item.categoryName} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span
            style={{
              width: 120,
              fontSize: 13,
              textAlign: 'right',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={item.categoryName}
          >
            {item.categoryName}
          </span>
          <div style={{ flex: 1, background: '#f5f5f5', height: 18, borderRadius: 3 }}>
            <div
              style={{
                width: `${(item.materialCount / max) * 100}%`,
                background: '#1677ff',
                height: '100%',
                borderRadius: 3,
                minWidth: item.materialCount > 0 ? 4 : 0,
              }}
            />
          </div>
          <span style={{ width: 96, fontSize: 13, color: '#666' }}>
            {item.materialCount} 种 · {(Number(item.ratio) * 100).toFixed(1)}%
          </span>
        </div>
      ))}
    </div>
  )
}
