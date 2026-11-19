import axios from 'axios'
import { ElMessage } from 'element-plus'
import { expireSession, getToken } from './auth'

export class ApiError extends Error {
  constructor(message, { code = 0, status = 0, config, cause } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.config = config
    this.cause = cause
  }
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  // 接口模块只使用本站相对路径，避免绝对地址意外携带本站令牌。
  if (/^(?:[a-z][a-z\d+.-]*:)?\/\//i.test(config.url || '')) {
    throw new ApiError('接口地址必须为相对路径', { config })
  }
  const token = config.skipAuth ? '' : getToken()
  config._authToken = token
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  else config.headers.delete('Authorization')
  return config
})

async function readPayload(data) {
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      return JSON.parse(await data.text())
    } catch {
      return null
    }
  }
  return data && typeof data === 'object' ? data : null
}

function fail(message, code, status, config = {}, cause) {
  const unauthorized = status === 401 || code === 401
  let showMessage = !config.silent
  if (unauthorized && !config.skipAuth) {
    showMessage = expireSession(config._authToken) && showMessage
  }
  if (showMessage) ElMessage.error(message)
  return Promise.reject(new ApiError(message, { code, status, config, cause }))
}

request.interceptors.response.use(
  async (response) => {
    const { config, status, data } = response
    const isBlob = typeof Blob !== 'undefined' && data instanceof Blob
    const jsonBlob = isBlob && /json/i.test(data.type || response.headers['content-type'] || '')
    if (config.responseType === 'blob' && isBlob && !jsonBlob && status >= 200 && status < 300) {
      if (config.expectedFileType !== 'xlsx') return data
      // XLSX is a ZIP container. JSON errors with octet-stream must never become fake Excel files.
      const signature = new Uint8Array(await data.slice(0, 4).arrayBuffer())
      if (
        signature[0] === 0x50 &&
        signature[1] === 0x4b &&
        signature[2] === 0x03 &&
        signature[3] === 0x04
      )
        return data
      const invalid = await readPayload(data)
      return fail(
        invalid?.message || '服务未返回有效的 Excel 文件，请稍后重试',
        invalid?.code || status,
        status,
        config,
      )
    }
    const payload = await readPayload(data)
    if (status >= 200 && status < 300 && payload?.code === 200) return payload.data
    return fail(
      payload?.message || '服务返回格式异常，请稍后重试',
      payload?.code || status,
      status,
      config,
    )
  },
  async (error) => {
    if (axios.isCancel(error)) return Promise.reject(error)
    if (error instanceof ApiError)
      return fail(error.message, error.code, error.status, error.config, error)
    const config = error.config || {}
    const status = error.response?.status || 0
    const payload = await readPayload(error.response?.data)
    const fallback =
      error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
        ? '请求超时，请稍后重试'
        : status === 401
          ? '登录已过期，请重新登录'
          : status === 403
            ? '没有操作权限'
            : status >= 500
              ? '服务暂时不可用，请稍后重试'
              : status
                ? '请求失败，请稍后重试'
                : '无法连接服务，请检查网络或后端是否启动'
    return fail(payload?.message || fallback, payload?.code || status, status, config, error)
  },
)

export default request
