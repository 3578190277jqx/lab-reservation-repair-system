const KEY = 'lab_student_session'
function read() { return wx.getStorageSync(KEY) || null }
function clear() { wx.removeStorageSync(KEY); wx.removeStorageSync('lab_manage_context') }
function save(value) {
  wx.setStorageSync(KEY, { token: value.token, user: value.user,
    expiresAt: Date.now() + value.expiresIn * 1000 })
}
function requireStudent() {
 if (!requireLogin()) return false
 if (read().user.role !== 'student') {
  wx.switchTab({url:'/pages/equipment/index'}); return false
 }
 return true
}
function requireLogin() {
 const s = read()
 if (!s || s.expiresAt <= Date.now() || !s.user || !['student','admin','lab_admin'].includes(s.user.role)) {
  clear(); wx.reLaunch({url:'/pages/login/index'}); return false
 }
 return true
}
function manager() { const s=read(); return !!(s && s.user && ['admin','lab_admin'].includes(s.user.role)) }
module.exports = { read, save, clear, requireStudent, requireLogin, manager }
