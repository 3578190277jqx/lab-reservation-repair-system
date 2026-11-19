const session = require('./session')
const ui = require('./ui')
const KEY = 'lab_tab_filter'
const counts = { equipment: ui.equipmentStatus.length, reservations: ui.reservationStatus.length, repairs: ui.repairStatus.length }
function open(kind, status, onFailure) {
  if (!(kind in counts) || !session.requireLogin()) return
  const cached = session.read()
  const filter = { token: cached.token, role: cached.user.role, kind, status: Number.isInteger(status) && status >= 0 && status < counts[kind] ? status : null }
  wx.setStorageSync(KEY, filter)
  wx.switchTab({ url: '/pages/' + kind + '/index', fail(error) { wx.removeStorageSync(KEY); if (typeof onFailure === 'function') onFailure(error) } })
}
function consume(kind) {
  const saved = wx.getStorageSync(KEY), current = session.read()
  if (!saved || !saved.kind) return null
  if (!current || saved.token !== current.token || saved.role !== current.user.role) { wx.removeStorageSync(KEY); return null }
  if (saved.kind !== kind) return null
  wx.removeStorageSync(KEY)
  return { name: '', scopeIndex: 0, statusIndex: Number.isInteger(saved.status) && saved.status >= 0 && saved.status < counts[kind] ? saved.status + 1 : 0 }
}
module.exports = { open, consume }
