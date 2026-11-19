// Run with: node --test tests/request.test.js (no dependencies).
const { test } = require('node:test')
const assert = require('node:assert/strict')
let saved, reply, uploadReply, captured, redirects = 0
global.wx = {
  getStorageSync: () => saved, setStorageSync: (_, v) => { saved = v }, removeStorageSync: () => { saved = null },
  reLaunch: () => { redirects++ },
  request: o => { captured = o; o.success(reply) },
  uploadFile: o => { captured = o; o.success(uploadReply) },
  downloadFile: o => { captured = o; o.success({ statusCode: 200, tempFilePath: '/tmp/image' }) }
}
const session = require('../utils/session'), api = require('../api/index'), transport = require('../utils/request')
const ui = require('../utils/ui')
function login(token = 'test') { session.save({ token, expiresIn: 604800, user: { role: 'student' } }) }
test('request compatibility, authentication, upload and image boundaries', async () => {
  login(); reply = { statusCode: 200, data: { code: 200, data: { total: 3, records: [] } } }
  assert.equal((await api.equipment({ current: 1 })).total, 3)
  assert.equal(captured.header.Authorization, 'Bearer test')
  assert.equal(captured.url, require('../config').baseURL.replace(/\/$/,'')+'/equipment/page')
  await api.login({ username: 'a', password: 'b' }); assert.equal(captured.header.Authorization, undefined)
  reply = { statusCode: 409, data: { code: 40901, message: '时段已被占用' } }
  await assert.rejects(api.apply({}), /时段已被占用/); assert.ok(session.read())
  reply = { statusCode: 401, data: { code: 401, message: 'expired' } }
  await assert.rejects(api.info(), /expired/); assert.equal(session.read(), null); assert.equal(redirects, 1)
  login(); uploadReply = { statusCode: 200, data: JSON.stringify({ code: 200, data: { imageUrl: '/file/images/1/' + 'a'.repeat(32) + '.png' } }) }
  const image = await api.upload('/tmp/a.png'); assert.equal(captured.name, 'file'); assert.equal(captured.header.Authorization, 'Bearer test')
  assert.equal(captured.header['Content-Type'], undefined)
  assert.equal(await api.image(image.imageUrl), '/tmp/image'); assert.equal(captured.header.Authorization, 'Bearer test')
  await assert.rejects(api.image('https://example.com/a.png'), /图片路径无效/)
  uploadReply = { statusCode: 200, data: '<html>Error</html>' }; await assert.rejects(api.upload('x'), /格式错误/)
  const original = wx.request
  wx.request = o => { login('new'); o.success({ statusCode: 401, data: { code: 401 } }) }
  await assert.rejects(api.info()); assert.equal(session.read().token, 'new'); wx.request = original
  assert.equal(ui.time('2026-10-01 09:00:00'), Date.parse('2026-10-01T01:00:00Z'))
  await assert.rejects(transport.request('//evil.test/path'), /无效接口路径/)
})
