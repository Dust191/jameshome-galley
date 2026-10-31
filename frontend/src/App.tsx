import { useQuery } from '@tanstack/react-query'

type Health = { status: string; version: string }

async function fetchHealth(): Promise<Health> {
  const res = await fetch('/healthz')
  if (!res.ok) throw new Error(`健康检查失败：${res.status}`)
  return (await res.json()) as Health
}

export default function App() {
  const { data, isPending, isError } = useQuery({ queryKey: ['health'], queryFn: fetchHealth })

  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: 24, lineHeight: 1.7 }}>
      <h1 style={{ marginBottom: 4 }}>jameshome-galley</h1>
      <p style={{ marginTop: 0, color: '#666' }}>团体厨房库存远程查看系统</p>

      <section style={{ marginTop: 24 }}>
        <h2 style={{ fontSize: 16 }}>运行状态</h2>
        {isPending && <p>检测中…</p>}
        {isError && <p style={{ color: '#b00020' }}>后端未就绪，请确认 `make dev` 已启动</p>}
        {data && (
          <p>
            status=<code>{data.status}</code>
            {' · '}
            version=<code>{data.version}</code>
          </p>
        )}
      </section>

      <p style={{ marginTop: 32, color: '#888', fontSize: 14 }}>
        骨架阶段：尚无业务页面。库存记录、修改与统计将在 0.1.0 交付。
      </p>
    </main>
  )
}
