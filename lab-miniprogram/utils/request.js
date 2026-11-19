const config = require('../config')
const session = require('./session')
const { networkMessage } = require('./network-error')
// Only send tokens to our configured backend, never to arbitrary attachment URLs.
function url(path) {
  if(!config.baseURL) throw new Error('服务地址尚未配置，请联系管理员')
  if (!/^\/(?!\/)/.test(path) || path.includes('://')) throw new Error('无效接口路径')
  return config.baseURL.replace(/\/$/, '') + path
}
function headers(anonymous, s) {
  return !anonymous && s ? { Authorization: 'Bearer ' + s.token } : {}
}
// A successful old response is as stale as an old 401 after switching accounts.
function checkSession(snapshot) {
  const current = session.read()
  if ((snapshot && snapshot.token) !== (current && current.token)) {
    const error = new Error('登录账号已变化，请重新加载')
    error.code = 'ERR_AUTH_CHANGED'
    throw error
  }
}
function networkError(message, anonymous, snapshot, cause) {
  try { if (!anonymous) checkSession(snapshot) } catch (error) { return error }
  return new Error(networkMessage(cause, message))
}
function expired(snapshot) {
  const current = session.read()
  // An old request must not clear a newer login session.
  if (snapshot && current && snapshot.token === current.token) {
    session.clear(); wx.reLaunch({ url: '/pages/login/index' })
  }
}
function decode(res, anonymous, snapshot) {
  if (!anonymous) checkSession(snapshot)
  let body = res.data
  if (typeof body === 'string') {
    try { body = JSON.parse(body) } catch (_) { throw new Error('服务器返回格式错误') }
  }
  if ((res.statusCode === 401 || (body && body.code === 401)) && !anonymous) expired(snapshot)
  if (res.statusCode < 200 || res.statusCode >= 300 || !body || body.code !== 200) {
    throw new Error((body && body.message) || '请求失败，请稍后重试')
  }
  return body.data
}
function request(path, method = 'GET', data = {}, anonymous = false, timeout = 20000) {
  const snapshot = session.read()
  return new Promise((resolve, reject) => wx.request({
    url: url(path), method, data, timeout,
    header: { 'Content-Type': 'application/json', ...headers(anonymous, snapshot) },
    success(res) { try { resolve(decode(res, anonymous, snapshot)) } catch (e) { reject(e) } },
    fail(cause) { reject(networkError('无法连接服务器，请检查网络和接口地址', anonymous, snapshot, cause)) }
  }))
}
function upload(filePath) {
  const snapshot = session.read()
  return new Promise((resolve, reject) => wx.uploadFile({
    url: url('/file/upload'), filePath, name: 'file', header: headers(false, snapshot), timeout: 30000,
    success(res) { try { resolve(decode(res, false, snapshot)) } catch (e) { reject(e) } },
    fail(cause) { reject(networkError('图片上传失败，请重试', false, snapshot, cause)) }
  }))
}
function image(path) {
  if (!/^\/file\/images\/[1-9][0-9]{0,18}\/[0-9a-f]{32}\.(jpg|png)$/.test(path)) return Promise.reject(new Error('图片路径无效'))
  const snapshot = session.read()
  return new Promise((resolve, reject) => wx.downloadFile({
    url: url(path), header: headers(false, snapshot), timeout: 30000,
    success(res) {
      try {
        checkSession(snapshot)
        if (res.statusCode === 401) expired(snapshot)
        if (res.statusCode === 200) resolve(res.tempFilePath)
        else reject(new Error('图片读取失败'))
      } catch (error) { reject(error) }
    }, fail(cause) { reject(networkError('图片下载失败', false, snapshot, cause)) }
  }))
}
module.exports = { request, upload, image }
