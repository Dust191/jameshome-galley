import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, UnauthorizedError } from '../../lib/api'
import type { Me, Role } from '../../lib/types'

/** 当前登录人与登录态。启动时拉一次 /auth/me，不缓存假登录态。 */
type AuthState = {
  me: Me | null
  loading: boolean
  refresh: () => Promise<void>
  signIn: (username: string, password: string) => Promise<Me>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setMe(await api.get<Me>('/api/v1/auth/me'))
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        setMe(null)
        return
      }
      throw error
    }
  }, [])

  useEffect(() => {
    refresh().finally(() => setLoading(false))
  }, [refresh])

  const signIn = useCallback(async (username: string, password: string) => {
    const current = await api.post<Me>('/api/v1/auth/login', { username, password })
    setMe(current)
    return current
  }, [])

  const signOut = useCallback(async () => {
    await api.post<void>('/api/v1/auth/logout')
    setMe(null)
  }, [])

  const value = useMemo(
    () => ({ me, loading, refresh, signIn, signOut }),
    [me, loading, refresh, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用')
  }
  return context
}

/** 能否录入数据：录入员与管理员可以，只读不可以。 */
export function canWrite(role: Role | undefined): boolean {
  return role === 'admin' || role === 'editor'
}

export function isAdmin(role: Role | undefined): boolean {
  return role === 'admin'
}
