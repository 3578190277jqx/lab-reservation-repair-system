// Public page and navigation contracts, exercised with an in-memory wx/API.
const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const storage = new Map()
global.wx = {
  getStorageSync: key => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: key => storage.delete(key),
  reLaunch() {}, stopPullDownRefresh() {}, switchTab() {},
  setTabBarStyle() {}, setTabBarItem() {}, setNavigationBarTitle() {}, setNavigationBarColor() {}
}
const api = require('../api/index')
const session = require('../utils/session')
const ui = require('../utils/ui')
const tabs = require('../utils/tab-route')
const listPage = require('../utils/list-page')
const workspace = require('../utils/workspace')
const { reservationTimeline } = require('../utils/reservation-detail')
let statsDefinition
global.Page = definition => { statsDefinition = definition }
require('../pages/stats/index')
const expired = {
  id: 12, status: 5, startTime: '2026-11-03 09:00:00', endTime: '2026-11-03 17:00:00',
  applyTime: '2026-11-02 12:00:00', updateTime: '2026-11-03 09:00:35',
  auditTime: null, auditBy: null, auditRemark: '预约开始仍未审核，系统自动关闭'
}
const instance = definition => ({ ...definition, data: structuredClone(definition.data), setData(value) { Object.assign(this.data, value) } })
beforeEach(() => {
  storage.clear()
  session.save({ token: 'student-token', expiresIn: 3600, user: { id: 1, role: 'student' } })
})

test('expired reservation is shown as its own status without cancel, sign-in, or no-show', async () => {
  api.reservations = async () => ({ records: [expired], total: 1 })
  const p = instance(listPage('reservations'))
  await p.onShow()
  assert.equal(ui.reservationStatus[5], '审核超时')
  assert.equal(p.data.rows[0].statusText, '审核超时')
  assert.equal(p.data.rows[0].canCancel, false)
  assert.equal(p.data.rows[0].canSignIn, false)
  p.onUnload()
})

test('status five is selectable and survives tab navigation as API status five', async () => {
  let query
  api.reservations = async q => { query = q; return { records: [expired], total: 1 } }
  tabs.open('reservations', 5)
  const p = instance(listPage('reservations'))
  await p.onShow()
  assert.equal(p.data.statusIndex, 6)
  assert.equal(p.data.statuses[6], '审核超时')
  assert.equal(query.status, 5)
  p.onUnload()
})

test('expired detail shows a system event and the actual processing time without a fake audit or no-show', () => {
  const events = reservationTimeline(expired)
  assert.deepEqual(events, [
    { title: '提交申请', time: expired.applyTime, content: '' },
    { title: '审核超时', time: expired.updateTime, content: expired.auditRemark }
  ])
})

test('home workspace retains the expired status text in recent personal reservations', async () => {
  api.equipment = async () => ({ records: [], total: 0 })
  api.reservations = async () => ({ records: [expired], total: 1 })
  api.repairs = async () => ({ records: [], total: 0 })
  const result = await workspace.load(false)
  assert.equal(result.reservations.rows[0].statusText, '审核超时')
})

test('manager overview shows the timeout count only when the server supplies it', async () => {
  session.save({ token: 'manager-token', expiresIn: 3600, user: { id: 2, role: 'lab_admin' } })
  const p = instance(statsDefinition)
  p.active = true
  api.overview = async () => ({ expiredReservationCount: 3 })
  await p.load()
  assert.deepEqual(p.data.rows.find(row => row.name === '最近30天审核超时预约'), { name: '最近30天审核超时预约', value: 3 })
  api.overview = async () => ({})
  await p.load()
  assert.equal(p.data.rows.some(row => row.name === '最近30天审核超时预约'), false)
})
