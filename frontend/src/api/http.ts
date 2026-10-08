export interface Result<T> {
  code: number
  message: string
  data: T
  requestId: string | null
}

export type ApiErrorKind = 'http' | 'business' | 'network' | 'contract' | 'aborted'

export class ApiError extends Error {
  constructor(
    public readonly kind: ApiErrorKind,
    public readonly status: number | null,
    public readonly code: number | null,
    message: string,
    public readonly requestId: string | null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface HttpOptions {
  method?: 'GET' | 'POST' | 'PUT'
  body?: unknown
  signal?: AbortSignal
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function requestId(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

// Validate the transport envelope; DTO shapes are described by TypeScript.
export async function http<T>(
  path: string,
  options: HttpOptions = {},
): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers: options.body === undefined
        ? { Accept: 'application/json' }
        : { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch {
    throw new ApiError(options.signal?.aborted ? 'aborted' : 'network', null, null,
      options.signal?.aborted ? '请求已取消' : '网络请求失败，请重新读取状态', null)
  }

  const headerId = requestId(response.headers.get('X-Request-Id'))
  let payload: unknown
  try {
    payload = await response.json()
  } catch (error) {
    const aborted = options.signal?.aborted
    const network = error instanceof TypeError
    throw new ApiError(aborted ? 'aborted' : network ? 'network' : response.ok ? 'contract' : 'http',
      response.status, null,
      aborted ? '请求已取消' : network ? '响应读取失败，请重新读取状态' : '服务器未返回有效 JSON 响应', headerId)
  }

  const record = isObject(payload) ? payload : {}
  const id = requestId(record.requestId) ?? headerId
  const code = typeof record.code === 'number' && Number.isSafeInteger(record.code) ? record.code : null
  const message = typeof record.message === 'string' && record.message.trim() !== ''
    ? record.message : null
  if (!response.ok) {
    throw new ApiError('http', response.status, code, message ?? 'HTTP 请求失败', id)
  }
  if (code === null || message === null || !Object.hasOwn(record, 'data')) {
    throw new ApiError('contract', response.status, code, '服务器响应格式不符合约定', id)
  }
  if (code !== 0) {
    throw new ApiError('business', response.status, code, message, id)
  }
  return record.data as T
}
