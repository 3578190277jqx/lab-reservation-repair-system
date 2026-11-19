const session = require('../../utils/session')
const appearance = require('../../utils/appearance')
const workspace = require('../../utils/workspace')
const tabs = require('../../utils/tab-route')
const empty = () => ({ equipment: null, overview: null, reservations: null, repairs: null, errors: {} })
Page({
  data: { manager: false, displayName: '', roleName: '', busy: false, ...empty() },
  onShow() {
    this.active = true
    if (!session.requireLogin()) { this.setData(empty()); return }
    const current = session.read()
    this.setData({ manager: appearance.apply('home'), displayName: current.user.realName || current.user.username || '同学',
      roleName: current.user.role === 'admin' ? '超级管理员' : current.user.role === 'lab_admin' ? '实验室管理员' : '学生' })
    return this.load()
  },
  onHide() { this.active = false; this.version = (this.version || 0) + 1; this.setData({ busy: false }); wx.stopPullDownRefresh() },
  onUnload() { this.onHide() },
  onPullDownRefresh() { return this.load() },
  async load() {
    if (!this.active || !session.requireLogin()) { wx.stopPullDownRefresh(); return }
    const { token, user } = session.read(), role = user.role
    const version = this.version = (this.version || 0) + 1
    this.setData({ ...empty(), busy: true })
    const current = () => this.active && this.version === version && session.read() && session.read().token === token && session.read().user.role === role
    try {
      const result = await workspace.load(session.manager())
      if (current()) this.setData(result)
    } finally { if (current()) { this.setData({ busy: false }); wx.stopPullDownRefresh() } }
  },
  browse(e) { const { kind, status } = e.currentTarget.dataset; tabs.open(kind, status == null || status === '' ? null : Number(status)) },
  apply(e) { if (session.requireStudent()) wx.navigateTo({ url: '/pages/apply/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
  stats() { if (session.requireLogin() && session.manager()) wx.navigateTo({ url: '/pages/stats/index' }) },
  handle(e) {
    if (!session.requireLogin() || !session.manager()) return
    const { kind, id } = e.currentTarget.dataset
    if (!['reservations', 'repairs'].includes(kind)) return
    const panel = this.data[kind], row = panel && panel.rows.find(item => String(item.id) === String(id))
    if (!row || row.status !== 0) return
    wx.setStorageSync('lab_manage_context', { token: session.read().token, kind, row })
    wx.navigateTo({ url: '/pages/manage/index?kind=' + kind + '&id=' + encodeURIComponent(id) + '&action=' + (kind === 'reservations' ? 'audit' : 'accept') })
  }
})
