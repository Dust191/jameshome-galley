/**
 * 后端接口封装。
 *
 * 三件事必须在这里统一，否则每个页面都会各写一遍：
 *
 * 1. **CSRF**：后端用 Cookie 会话 + CSRF 双 Cookie。写请求必须带 `X-XSRF-TOKEN`，
 *    值从 `XSRF-TOKEN` Cookie 里读。首次调用前要先 GET 一次 `/auth/csrf` 让服务端下发。
 * 2. **错误体**：后端统一返回 `{code, message, field, traceId}`，这里转成带类型的异常，
 *    页面据此把消息挂到具体字段上。
 * 3. **401**：会话过期时统一跳登录，不在每个页面判断。
 */

export type ApiErrorBody = {
  code: string
  message: string
  field: string | null
  traceId: string | null
}

export class ApiError extends Error {
  readonly code: string
  readonly field: string | null
  readonly status: number
  readonly traceId: string | null

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.field = body.field
    this.traceId = body.traceId
  }
}

/** 会话失效时抛这个，路由层统一跳登录。 */
export class UnauthorizedError extends Error {
  constructor() {
    super('请先登录')
    this.name = 'UnauthorizedError'
  }
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) {
      return decodeURIComponent(trimmed.slice(prefix.length))
    }
  }
  return null
}

/** 让服务端下发 CSRF Cookie；登录、注册、改密前都要先调一次。 */
export async function ensureCsrf(): Promise<void> {
  if (readCookie('XSRF-TOKEN')) {
    return
  }
  await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  formData?: FormData
  signal?: AbortSignal
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = {}

  if (method !== 'GET') {
    await ensureCsrf()
    const token = readCookie('XSRF-TOKEN')
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  let body: BodyInit | undefined
  if (options.formData) {
    body = options.formData
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  const response = await fetch(path, {
    method,
    headers,
    body,
    credentials: 'same-origin',
    signal: options.signal,
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) {
    const errorBody: ApiErrorBody = payload ?? {
      code: 'UNKNOWN',
      message: `请求失败（${response.status}）`,
      field: null,
      traceId: null,
    }
    throw new ApiError(response.status, errorBody)
  }
  return payload as T
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, formData: FormData) => request<T>(path, { method: 'POST', formData }),
}

/** 查询串拼装：空值一律不拼。 */
export function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}
