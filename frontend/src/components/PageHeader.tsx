import type { ReactNode } from 'react'
import { Space, Typography } from 'antd'

/** 页标题 + 右上主操作。一页最多一个主按钮（DES-003 §3）。 */
export default function PageHeader({
  title,
  description,
  extra,
}: {
  title: string
  description?: string
  extra?: ReactNode
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        marginBottom: 16,
        gap: 16,
      }}
    >
      <div>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        {description && (
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {description}
          </Typography.Text>
        )}
      </div>
      {extra && <Space>{extra}</Space>}
    </div>
  )
}
