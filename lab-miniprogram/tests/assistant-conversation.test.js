const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const storage = new Map()
let sent, reply, copied
global.wx = {
  getStorageSync: k => storage.get(k), setStorageSync: (k, v) => storage.set(k, v), removeStorageSync: k => storage.delete(k),
  reLaunch() {}, setClipboardData(o) { copied = o.data; o.success?.() }, showToast() {},
}
const session = require('../utils/session'), transport = require('../utils/request')
transport.request = async (path, method, data) => {
  if (path.endsWith('/options')) return { enabled: true }
  sent = data
  return new Promise((resolve, reject) => { reply = { resolve, reject } })
}
let definition
global.Page = d => { definition = d }
require('../pages/assistant/index')
const page = () => ({ ...definition, data: structuredClone(definition.data), setData(v) { Object.assign(this.data, v) } })
beforeEach(() => { session.save({ token: 'A', expiresIn: 3600, user: { role: 'student', id: 1 } }); sent = null; copied = null })
async function answer(p, question, mode = 'model', text = '设备黑屏，需管理员检查。') {
  p.setData({ question }); const pending = p.ask(); reply.resolve({ mode, answer: text }); await pending
}
test('follow-up includes the previous successful question and answer', async () => {
  const p = page(); p.onShow(); await answer(p, '帮我整理描述：设备黑屏')
  await answer(p, '再简短一点')
  assert.deepEqual(sent.history, [{ question: '帮我整理描述：设备黑屏', answer: '设备黑屏，需管理员检查。' }])
})
test('only three successful model turns are sent, excluding fallback', async () => {
  const p = page(); p.onShow()
  for (let i = 1; i <= 4; i++) await answer(p, `问题${i}`, 'model', `回答${i}`)
  await answer(p, '模型失败', 'unavailable', '操作指南')
  await answer(p, '继续')
  assert.deepEqual(sent.history, [2, 3, 4].map(i => ({ question: `问题${i}`, answer: `回答${i}` })))
})
test('retry replaces failure and preserves edited draft with original context', async () => {
  const p = page(); p.onShow(); await answer(p, '先整理故障')
  p.setData({ question: '再简短一点' }); const pending = p.ask(); reply.reject(new Error('网络错误')); await pending
  assert.equal(p.data.messages.length, 2); assert.equal(p.data.messages[1].retryable, true)
  p.input({ detail: { value: '尚未发送的新草稿' } })
  const retry = p.retry(); assert.equal(sent.question, '再简短一点'); assert.equal(sent.history.length, 1)
  reply.resolve({ mode: 'model', answer: '黑屏待检。' }); await retry
  assert.equal(p.data.messages.length, 2); assert.equal(p.data.messages[1].answer, '黑屏待检。')
  assert.equal(p.data.question, '尚未发送的新草稿')
})
test('clear discards late reply and resets next request history', async () => {
  const p = page(); p.onShow(); await answer(p, '私密草稿')
  p.setData({ question: '追问' }); const pending = p.ask(); p.clear()
  reply.resolve({ mode: 'model', answer: '旧回答' }); await pending
  assert.deepEqual(p.data.messages, [])
  await answer(p, '新话题'); assert.deepEqual(sent.history, [])
})
test('copy transfers only the selected answer and cannot copy after hide', async () => {
  const p = page(); p.onShow(); await answer(p, '设备问题', 'model', '复制这一条')
  p.copy({ currentTarget: { dataset: { id: p.data.messages[0].id } } }); assert.equal(copied, '复制这一条')
  p.onHide(); copied = null; p.copy({ currentTarget: { dataset: { id: 1 } } }); assert.equal(copied, null)
})
test('failed tab navigation keeps current model request usable and shows failure', async () => {
  wx.switchTab = o => o.fail({ errMsg: 'navigation failed' })
  const p = page(); p.onShow(); p.setData({ question: '帮我整理故障现象' })
  const pending = p.ask(); p.go('equipment')
  assert.equal(p.data.busy, true); assert.match(p.data.error, /页面暂时无法打开/)
  reply.resolve({ mode: 'model', answer: '保留回答' }); await pending
  assert.equal(p.data.messages[0].answer, '保留回答'); assert.equal(p.data.messages[0].loading, false)
})
