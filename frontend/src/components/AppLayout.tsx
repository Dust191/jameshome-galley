import { useMemo, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Dropdown, Layout, Menu, Spin, Typography, theme } from 'antd'
import type { ItemType } from 'antd/es/menu/interface'
import {
  BarChartOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HomeOutlined,
  KeyOutlined,
  LogoutOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { isAdmin, useAuth } from '../features/auth/AuthContext'

const { Header, Sider, Content } = Layout

/**
 * 应用骨架：顶栏 + 侧栏 + 内容区（DES-003 §3）。
 *
 * <p>Header 与 Sider 均 position: sticky（返工要求 7）：长列表滚动到深处时导航不消失。
 * 菜单按角色过滤而非置灰——只读账号看到一排点不动的菜单只会反复试探。
 */
export default function AppLayout() {
  const { me, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken()

  const items = useMemo<ItemType[]>(() => {
    const base: ItemType[] = [
      { key: '/', icon: <HomeOutlined />, label: <Link to="/">概览</Link> },
      { key: '/materials', icon: <DatabaseOutlined />, label: <Link to="/materials">物料</Link> },
      { key: '/movements', icon: <FileTextOutlined />, label: <Link to="/movements">流水</Link> },
    ]
    if (isAdmin(me?.role)) {
      base.push(
        { key: '/stats', icon: <BarChartOutlined />, label: <Link to="/stats">统计</Link> },
        {
          key: '/admin',
          icon: <TeamOutlined />,
          label: '管理',
          children: [
            {
              key: '/admin/users',
              icon: <TeamOutlined />,
              label: <Link to="/admin/users">账号</Link>,
            },
            {
              key: '/admin/invites',
              icon: <KeyOutlined />,
              label: <Link to="/admin/invites">邀请码</Link>,
            },
          ],
        },
      )
    }
    return base
  }, [me?.role])

  const selectedKey = useMemo(() => {
    const path = location.pathname
    if (path.startsWith('/materials')) return '/materials'
    if (path.startsWith('/movements')) return '/movements'
    if (path.startsWith('/admin/users')) return '/admin/users'
    if (path.startsWith('/admin/invites')) return '/admin/invites'
    if (path.startsWith('/stats')) return '/stats'
    return '/'
  }, [location.pathname])

  if (!me) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingInline: 20,
          height: 56,
          lineHeight: '56px',
          background: colorBgContainer,
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <span style={{ display: 'inline-flex', alignItems: 'baseline', gap: 8 }}>
          <Typography.Text strong style={{ fontSize: 16 }}>
            雅各家厨房
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {"James' Home Galley"}
          </Typography.Text>
        </span>
        <Dropdown
          menu={{
            items: [
              {
                key: 'profile',
                icon: <UserOutlined />,
                disabled: true,
                label: `${me.displayName}（${me.role === 'admin' ? '管理员' : me.role === 'editor' ? '录入员' : '只读'}）`,
              },
              { type: 'divider' as const },
              {
                key: 'password',
                label: '修改密码',
                onClick: () => navigate('/password'),
              },
              {
                key: 'logout',
                icon: <LogoutOutlined />,
                label: '退出',
                onClick: async () => {
                  await signOut()
                  navigate('/login')
                },
              },
            ],
          }}
        >
          <span style={{ cursor: 'pointer', display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            <Avatar size="small">{me.displayName.slice(0, 1)}</Avatar>
            <span>{me.displayName}</span>
          </span>
        </Dropdown>
      </Header>
      <Layout>
        <Sider
          width={200}
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          breakpoint="lg"
          collapsedWidth={64}
          theme="light"
          style={{
            // 固钉：内容滚动时侧栏保持可见
            position: 'sticky',
            top: 56,
            height: 'calc(100vh - 56px)',
            overflow: 'auto',
            borderRight: '1px solid #f0f0f0',
          }}
        >
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            defaultOpenKeys={['/admin']}
            items={items}
            style={{ borderInlineEnd: 'none' }}
          />
        </Sider>
        <Content style={{ padding: 20 }}>
          <div
            style={{
              background: colorBgContainer,
              borderRadius: borderRadiusLG,
              padding: 20,
              minHeight: '100%',
            }}
          >
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
