// 仅使用微信失败类型生成可操作提示，不展示可能携带敏感参数的原始URL或请求头。
function networkMessage(error, fallback) {
  const detail = String(error && error.errMsg || '').toLowerCase()
  if (/url not in domain list|domain list|合法域名/.test(detail)) {
    return '微信拦截了接口域名。开发预览请在手机右上角打开调试后重新进入；正式版需配置 HTTPS 合法域名。'
  }
  if (/ssl|tls|certificate|https/.test(detail)) return '安全连接失败，请检查服务器 HTTPS 证书和微信域名配置。'
  if (/timeout|timed out/.test(detail)) return '连接服务器超时，请切换网络后重试。'
  if (/network is down|network unavailable|not connected|disconnected|internet connection appears to be offline/.test(detail)) return '网络已断开，请检查 Wi-Fi 或移动数据。'
  return fallback
}
module.exports = { networkMessage }
