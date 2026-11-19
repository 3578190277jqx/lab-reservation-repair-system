const equipmentStatus = ['可用', '已预约', '维修中', '已报废']
const reservationStatus = ['待审核', '已通过', '已拒绝', '已取消', '爽约', '审核超时']
const repairStatus = ['待受理', '已受理', '维修中', '已完成']
function input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) }
function error(e) {
  // Account switches discard old requests without showing an unrelated error.
  if (e.code === 'ERR_AUTH_CHANGED') return
  this.setData({ error: e.message || '操作失败' })
}
function confirm(content) { return new Promise(resolve => wx.showModal({ title: '请确认', content, success: r => resolve(r.confirm), fail: () => resolve(false) })) }
function success(title) { wx.showToast({ title, icon: 'success' }) }
// Backend timestamps are China local time. Use an explicit offset on all devices.
function time(value) { return Date.parse(value.replace(' ', 'T') + '+08:00') }
function dateNow() { return new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10) }
module.exports = { equipmentStatus, reservationStatus, repairStatus, input, error, confirm, success, time, dateNow }
