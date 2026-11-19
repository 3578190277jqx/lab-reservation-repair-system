const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
let saved, captured, redirects
global.wx = {
  getStorageSync: () => saved,
  setStorageSync: (_, value) => { saved = value },
  removeStorageSync: () => { saved = null },
  reLaunch() { redirects++ },
  request(options) { captured = options },
  uploadFile(options) { captured = options },
  downloadFile(options) { captured = options }
}
const session = require('../utils/session')
const transport = require('../utils/request')
const ui = require('../utils/ui')
function login(token) { session.save({ token, expiresIn: 3600, user: { role: 'student' } }) }
const changed = error => error.code === 'ERR_AUTH_CHANGED'
beforeEach(() => { redirects = 0; login('first') })

test('late successful authenticated response cannot expose a previous account', async () => {
  const promise = transport.request('/user/info')
  assert.equal(captured.header.Authorization, 'Bearer first')
  login('second')
  captured.success({ statusCode: 200, data: { code: 200, data: { username: 'first' } } })
  await assert.rejects(promise, changed)
  assert.equal(session.read().token, 'second')
  assert.equal(redirects, 0)
})

test('stale upload and image download results are rejected after account change', async () => {
  const upload = transport.upload('/tmp/test.png')
  login('second')
  captured.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: { imageUrl: '/private' } }) })
  await assert.rejects(upload, changed)
  const image = transport.image('/file/images/1/' + 'a'.repeat(32) + '.png')
  session.clear()
  captured.success({ statusCode: 200, tempFilePath: '/tmp/first-user-image.png' })
  await assert.rejects(image, changed)
})

test('old unauthorized and network failures leave the new session intact', async () => {
  const unauthorized = transport.request('/user/info')
  login('second')
  captured.success({ statusCode: 401, data: { code: 401 } })
  await assert.rejects(unauthorized, changed)
  const networkFailure = transport.request('/user/info')
  login('third'); captured.fail({ errMsg: 'timeout' })
  await assert.rejects(networkFailure, changed)
  assert.equal(session.read().token, 'third')
  assert.equal(redirects, 0)
})

test('anonymous login and current-session success still resolve normally', async () => {
  const loginRequest = transport.request('/user/login', 'POST', {}, true)
  assert.equal(captured.header.Authorization, undefined)
  session.clear()
  captured.success({ statusCode: 200, data: { code: 200, data: { token: 'new-login' } } })
  assert.equal((await loginRequest).token, 'new-login')
  login('current')
  const info = transport.request('/user/info')
  captured.success({ statusCode: 200, data: { code: 200, data: { username: 'current' } } })
  assert.equal((await info).username, 'current')
})

test('account-change errors are silently discarded by page error handling', () => {
  ui.error.call({ setData() { assert.fail('stale error rendered') } }, { code: 'ERR_AUTH_CHANGED' })
  let error
  ui.error.call({ setData(data) { error = data.error } }, new Error('network down'))
  assert.equal(error, 'network down')
})
