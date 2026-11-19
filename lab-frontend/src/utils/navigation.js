/** 只允许站内地址，并拒绝认证页面之间循环跳转；传给 router.replace 使用。 */
export function safeRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return ''
  let decoded
  try {
    decoded = decodeURIComponent(value)
  } catch {
    return ''
  }
  // Reject ASCII control characters in untrusted redirect targets, deliberately.
  // eslint-disable-next-line no-control-regex
  if (/[\\\u0000-\u0020\u007f]/.test(decoded) || decoded.startsWith('//')) return ''
  const path = decoded.split(/[?#]/)[0].replace(/\/+$/, '').toLowerCase()
  if (['/login', '/register', '/session-error'].includes(path)) return ''
  return value
}
