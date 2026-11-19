// 只保存令牌与过期时间。角色、用户资料每次刷新页面后均从后端重新读取。
const STORAGE_KEY = 'lab.auth.session'
const listeners = new Set()
let memorySession = null

function readSession() {
  try {
    const raw = globalThis.sessionStorage?.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : memorySession
  } catch {
    return memorySession
  }
}

export function getToken() {
  const session = readSession()
  if (!session || typeof session.token !== 'string' || !session.token.trim()
      || !Number.isFinite(session.expiresAt) || session.expiresAt <= Date.now()) {
    clearSession()
    if (typeof session?.token === 'string' && session.token && Number.isFinite(session.expiresAt)
        && session.expiresAt <= Date.now()) {
      // 延迟到当前调用栈之后，避免 Pinia 初始化读取令牌时触发自身重入。
      // 期间若已登录新账号，则不能用旧会话的到期事件退出新账号。
      Promise.resolve().then(() => {
        if (!readSession()) listeners.forEach((listener) => listener())
      })
    }
    return ''
  }
  return session.token
}

export function saveSession({ token, expiresIn }) {
  if (typeof token !== 'string' || !token.trim() || !Number.isFinite(expiresIn) || expiresIn <= 0) {
    throw new Error('登录响应缺少有效令牌或过期时间')
  }
  memorySession = { token, expiresAt: Date.now() + expiresIn * 1000 }
  try {
    globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(memorySession))
  } catch {
    // 浏览器禁用存储时仍可在当前页面使用；刷新后需要重新登录。
  }
}

export function clearSession() {
  memorySession = null
  try { globalThis.sessionStorage?.removeItem(STORAGE_KEY) } catch { /* 存储不可用不影响退出 */ }
}

export function onAuthExpired(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function expireSession(requestToken) {
  // 旧账号请求的迟到401，不能退出刚登录的新账号；同时避免并发401重复提示。
  // 比较原始会话：响应返回时即使刚好到期，也要通知路由退出，不先调用getToken清空。
  if (!requestToken || requestToken !== readSession()?.token) return false
  clearSession()
  listeners.forEach((listener) => listener())
  return true
}
