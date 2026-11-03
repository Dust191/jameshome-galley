import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import type { ReactElement } from 'react'
import { Result, Spin } from 'antd'
import AppLayout from './components/AppLayout'
import { canWrite, isAdmin, useAuth } from './features/auth/AuthContext'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import PasswordPage from './features/auth/PasswordPage'
import OverviewPage from './features/overview/OverviewPage'
import MaterialListPage from './features/materials/MaterialListPage'
import MaterialFormPage from './features/materials/MaterialFormPage'
import MovementListPage from './features/movements/MovementListPage'
import StatsPage from './features/stats/StatsPage'
import UsersPage from './features/admin/UsersPage'
import InvitesPage from './features/admin/InvitesPage'

/** 未登录一律跳登录，登录后回到原处。 */
function RequireAuth({ children }: { children: ReactElement }) {
  const { me, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }
  if (!me) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  // 待改密时只放行改密页，否则每个接口都会 403
  if (me.mustChangePassword && location.pathname !== '/password') {
    return <Navigate to="/password" replace />
  }
  return children
}

function RequireAdmin({ children }: { children: ReactElement }) {
  const { me } = useAuth()
  if (!isAdmin(me?.role)) {
    return <Result status="403" title="没有这个权限" subTitle="该页面仅管理员可见。" />
  }
  return children
}

/**
 * 新增/登记已改为列表页弹窗：旧地址跳到列表并打开弹窗。
 * 只读账号直连仍会被拦下，不会看到可填的表单。
 */
function OpenCreateModal({ to, subTitle }: { to: string; subTitle: string }) {
  const { me } = useAuth()
  if (!canWrite(me?.role)) {
    return <Result status="403" title="没有这个权限" subTitle={subTitle} />
  }
  return <Navigate to={to} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/password"
        element={
          <RequireAuth>
            <PasswordPage />
          </RequireAuth>
        }
      />

      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<OverviewPage />} />
        {/* 录入类页面对只读账号在组件内部渲染为"没有这个权限"或只读详情，不依赖路由层拦截 */}
        <Route path="/materials" element={<MaterialListPage />} />
        <Route
          path="/materials/new"
          element={
            <OpenCreateModal to="/materials?new=1" subTitle="新建物料需要录入员或管理员权限。" />
          }
        />
        <Route path="/materials/:id" element={<MaterialFormPage />} />
        <Route path="/movements" element={<MovementListPage />} />
        <Route
          path="/movements/new"
          element={
            <OpenCreateModal to="/movements?new=1" subTitle="登记出入库需要录入员或管理员权限。" />
          }
        />
        <Route
          path="/stats"
          element={
            <RequireAdmin>
              <StatsPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/admin/users"
          element={
            <RequireAdmin>
              <UsersPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/admin/invites"
          element={
            <RequireAdmin>
              <InvitesPage />
            </RequireAdmin>
          }
        />
      </Route>

      <Route path="*" element={<Result status="404" title="页面不存在" />} />
    </Routes>
  )
}
