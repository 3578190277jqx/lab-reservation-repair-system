// Run the actual list page with a deterministic clock and in-memory wx/API stubs.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const session = require('../utils/session')
const api = require('../api/index')
const listPage = require('../utils/list-page')

function harness(start) {
  const originalNow = Date.now, originalSet = global.setTimeout, originalClear = global.clearTimeout
  let now = Date.parse(start), sequence = 0, requests = 0
  const timers = new Map(), storage = new Map()
  Date.now = () => now
  global.setTimeout = (fn, delay) => { const id = ++sequence; timers.set(id, { fn, at: now + delay }); return id }
  global.clearTimeout = id => timers.delete(id)
  global.wx = {
    getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key), stopPullDownRefresh() {}, reLaunch() {}
  }
  session.save({ token: 'A', expiresIn: 7200, user: { id: 1, role: 'student' } })
  api.reservations = async () => { requests++; return { records: [
    { id: 1, status: 1, startTime: '2026-11-03 10:00:00', endTime: '2026-11-03 11:00:00' },
    { id: 2, status: 1, startTime: '2026-11-03 10:00:00', endTime: '2026-11-03 10:15:00' },
    { id: 3, status: 0, startTime: '2026-11-03 10:00:00', endTime: '2026-11-03 11:00:00' }
  ], total: 3 } }
  const def = listPage('reservations')
  const page = { ...def, data: structuredClone(def.data), setData(v) { Object.assign(this.data, v) } }
  function advance(ms) {
    const end = now + ms
    while (true) {
      const item = [...timers.entries()].filter(([, t]) => t.at <= end).sort((a, b) => a[1].at - b[1].at)[0]
      if (!item) break
      timers.delete(item[0]); now = item[1].at; item[1].fn()
    }
    now = end
  }
  return { page, advance, count: () => timers.size, requests: () => requests,
    restore() { Date.now = originalNow; global.setTimeout = originalSet; global.clearTimeout = originalClear } }
}

test('visible reservation eligibility changes at start, end and 30-minute boundary without network polling', async () => {
  const h = harness('2026-11-03T09:59:59+08:00')
  try {
    await h.page.onShow()
    assert.deepEqual(h.page.data.rows.map(r => [r.canCancel, r.canSignIn]), [[true, false], [true, false], [true, false]])
    h.advance(1000)
    assert.deepEqual(h.page.data.rows.map(r => [r.canCancel, r.canSignIn]), [[false, true], [false, true], [false, false]])
    h.advance(15 * 60000)
    assert.deepEqual(h.page.data.rows.map(r => r.canSignIn), [true, false, false])
    h.advance(15 * 60000)
    assert.deepEqual(h.page.data.rows.map(r => r.canSignIn), [false, false, false])
    assert.equal(h.requests(), 1)
    h.page.onHide()
    assert.equal(h.count(), 0)
  } finally { h.restore() }
})

test('hide and unload remove scheduled updates; later show reloads eligibility once', async () => {
  for (const event of ['onHide', 'onUnload']) {
    const h = harness('2026-11-03T09:59:59+08:00')
    try {
      await h.page.onShow(); assert.equal(h.count(), 1)
      h.page[event](); assert.equal(h.count(), 0)
      h.page.setData = () => assert.fail('hidden page received a clock update')
      h.advance(1000); assert.equal(h.requests(), 1)
    } finally { h.restore() }
  }
  const h = harness('2026-11-03T09:59:59+08:00')
  try {
    await h.page.onShow(); h.page.onHide(); h.advance(1000)
    await h.page.onShow(); assert.equal(h.page.data.rows[0].canSignIn, true)
    assert.equal(h.requests(), 2)
    h.page.onUnload(); assert.equal(h.count(), 0)
  } finally { h.restore() }
})

test('a changed account cannot update the previous account rows when a timer fires', async () => {
  const h = harness('2026-11-03T09:59:59+08:00')
  try {
    await h.page.onShow()
    session.save({ token: 'B', expiresIn: 7200, user: { id: 2, role: 'student' } })
    h.page.setData = () => assert.fail('stale timer wrote rows from previous account')
    h.advance(1000)
    assert.equal(h.count(), 0)
    assert.equal(h.requests(), 1)
  } finally { h.restore() }
})
