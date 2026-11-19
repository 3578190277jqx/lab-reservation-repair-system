// Deterministic deferred responses reproduce slow-network races without a server.
const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
let saved, pending, stops
global.wx = {
  getStorageSync: () => saved,
  setStorageSync: (_, value) => { saved = value },
  removeStorageSync: () => { saved = null },
  reLaunch() {},
  stopPullDownRefresh() { stops++ }
}
const session = require('../utils/session')
const api = require('../api/index')
const listPage = require('../utils/list-page')
function login(token) { session.save({ token, expiresIn: 3600, user: { role: 'student' } }) }
function page(kind = 'equipment') {
  const definition = listPage(kind)
  const instance = { ...definition, data: JSON.parse(JSON.stringify(definition.data)),
    setData(value) { Object.assign(this.data, value) } }
  instance.syncOwner(session.read().token)
  return instance
}
function result(id, total = 30) {
  return { records: [{ id, name: id, status: 0, startTime: '2099-01-01 08:00:00', endTime: '2099-01-01 09:00:00' }], total }
}
beforeEach(() => {
  pending = []; stops = 0; login('first')
  for (const kind of ['equipment', 'reservations', 'repairs']) {
    api[kind] = query => new Promise((resolve, reject) => pending.push({ query, resolve, reject }))
  }
})

test('new search wins even if its older request finishes last', async () => {
  const p = page()
  p.setData({ name: '旧关键词' }); const old = p.search()
  p.setData({ name: '新关键词' }); const latest = p.search()
  assert.equal(pending.length, 2)
  assert.equal(pending[1].query.name, '新关键词')
  pending[1].resolve(result('new')); await latest
  pending[0].resolve(result('old')); await old
  assert.deepEqual(p.data.rows.map(row => row.id), ['new'])
  assert.equal(p.data.busy, false)
})

test('stale failure cannot stop the current refresh indicator or set an error', async () => {
  const p = page()
  const old = p.search(), latest = p.onPullDownRefresh()
  const stoppedBefore = stops
  pending[0].reject(new Error('old server failure')); await old
  assert.equal(p.data.busy, true)
  assert.equal(p.data.error, '')
  assert.equal(stops, stoppedBefore)
  pending[1].resolve(result('new')); await latest
  assert.equal(p.data.busy, false)
  assert.equal(stops, stoppedBefore + 1)
})

test('load-more uses applied search filters and retries the failed page', async () => {
  const p = page()
  p.setData({ name: '示波器', statusIndex: 1 })
  const initial = p.search(); pending[0].resolve(result('first')); await initial
  p.setData({ name: '尚未提交的名称' })
  const next = p.more(), duplicate = p.more()
  assert.equal(pending.length, 2)
  assert.deepEqual(pending[1].query, { name: '示波器', status: 0, current: 2, size: 10 })
  pending[1].reject(new Error('暂时断网')); await next; await duplicate
  assert.equal(p.data.page, 1)
  assert.deepEqual(p.data.rows.map(row => row.id), ['first'])
  const retry = p.more()
  assert.equal(pending[2].query.current, 2)
  pending[2].resolve(result('second')); await retry
  assert.deepEqual(p.data.rows.map(row => row.id), ['first', 'second'])
})

test('filter change discards pending pagination and starts at page one', async () => {
  const p = page()
  const initial = p.search(); pending[0].resolve(result('first')); await initial
  const more = p.more()
  const filtered = p.filter({ detail: { value: '3' } })
  assert.deepEqual(pending[2].query, { status: 2, current: 1, size: 10 })
  assert.deepEqual(p.data.rows, [])
  pending[1].resolve(result('old second page')); await more
  assert.equal(p.data.busy, true)
  pending[2].resolve(result('repairing', 1)); await filtered
  assert.deepEqual(p.data.rows.map(row => row.id), ['repairing'])
  assert.equal(p.data.hasMore, false)
})

test('switching account starts a fresh load while an old account request is pending', async () => {
  const p = page('reservations')
  const old = p.onShow()
  login('second')
  const fresh = p.onShow()
  assert.equal(pending.length, 2)
  pending[0].resolve(result('private old user row')); await old
  assert.deepEqual(p.data.rows, [])
  assert.equal(p.data.busy, true)
  pending[1].resolve(result('new user row')); await fresh
  assert.deepEqual(p.data.rows.map(row => row.id), ['new user row'])
})

test('hidden or unloaded pages do not accept pending response writes', async () => {
  for (const event of ['onHide', 'onUnload']) {
    const p = page()
    const load = p.search(), index = pending.length - 1
    p[event]()
    p.setData = () => assert.fail('late response wrote into an inactive page')
    pending[index].resolve(result('late')); await load
  }
})

test('malformed page responses report an error without advancing the page', async () => {
  const p = page()
  const load = p.search(); pending[0].resolve({ records: null, total: 2 }); await load
  assert.match(p.data.error, /格式错误/)
  assert.equal(p.data.page, 0)
  assert.equal(p.data.busy, false)
})

test('confirmation opened by an old account cannot submit an action for the new account', async () => {
  let confirm, calls = 0
  wx.showModal = options => { confirm = options.success }
  api.cancel = () => { calls++; return Promise.resolve() }
  const p = page('reservations')
  const action = p.action({ currentTarget: { dataset: { id: 1, action: 'cancel' } } })
  login('second')
  confirm({ confirm: true })
  await action
  assert.equal(calls, 0)
})
