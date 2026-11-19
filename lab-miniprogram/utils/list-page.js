const appearance = require('./appearance'), tabs = require('./tab-route')
const api = require('../api/index'), session = require('./session'), ui = require('./ui')
function reservationEligibility(item, now, student) {
  const start = typeof item.startTime === 'string' ? ui.time(item.startTime) : NaN
  const end = typeof item.endTime === 'string' ? ui.time(item.endTime) : NaN
  const eligible = student && !item.signInTime
  return {
    canCancel: eligible && [0, 1].includes(item.status) && now < start,
    canSignIn: eligible && item.status === 1 && now >= start && now < Math.min(end, start + 30 * 60000)
  }
}
// Refresh/search supersedes old loads; pagination keeps the last submitted filters.
module.exports = function listPage(kind) {
  const labels = kind === 'equipment' ? ui.equipmentStatus : kind === 'reservations' ? ui.reservationStatus : ui.repairStatus
  return {
    data: { scopeIndex:0, scopes:['管理工单','我的报修'],manager: false, total: null, rows: [], page: 0, hasMore: true, busy: false, actionBusy: false, error: '', name: '', statusIndex: 0, statuses: ['全部状态', ...labels] },
    input: ui.input,
    onShow() {
      this.active = true
      this.clearEligibilityTimer()
      if (!session.requireLogin()) { this.setData({ rows: [] }); return }
      this.setData({manager: appearance.apply(kind)})
      const token = session.read().token
      this.syncOwner(token)
      const nextFilter = tabs.consume(kind)
      if (nextFilter) this.setData(nextFilter)
      if(kind==='repairs' && session.manager()){const next=wx.getStorageSync('lab_repair_scope');if(next&&next.token===token&&[0,1].includes(next.scopeIndex)){this.setData({scopeIndex:next.scopeIndex});wx.removeStorageSync('lab_repair_scope')}}
      return this.load(true)
    },
    onHide() { this.active = false; this.clearEligibilityTimer(); this.invalidateLoad() },
    onUnload() { this.active = false; this.clearEligibilityTimer(); this.invalidateLoad() },
    clearEligibilityTimer() {
      if (this.eligibilityTimer != null) clearTimeout(this.eligibilityTimer)
      this.eligibilityTimer = null
    },
    refreshReservationEligibility() {
      this.clearEligibilityTimer()
      const current = session.read()
      if (kind !== 'reservations' || this.active !== true || !current || !current.user ||
          current.user.role !== 'student' || current.token !== this.ownerToken ||
          current.user.role !== this.ownerRole || current.expiresAt <= Date.now()) return
      const now = Date.now()
      let next = Infinity, changed = false
      const rows = this.data.rows.map(item => {
        const flags = reservationEligibility(item, now, true)
        if (flags.canCancel !== item.canCancel || flags.canSignIn !== item.canSignIn) changed = true
        if (!item.signInTime && [0, 1].includes(item.status)) {
          const start = typeof item.startTime === 'string' ? ui.time(item.startTime) : NaN
          const end = typeof item.endTime === 'string' ? ui.time(item.endTime) : NaN
          const boundaries = item.status === 1 ? [start, Math.min(end, start + 30 * 60000)] : [start]
          for (const boundary of boundaries) if (Number.isFinite(boundary) && boundary > now) next = Math.min(next, boundary)
        }
        return { ...item, ...flags }
      })
      if (changed) this.setData({ rows })
      if (next !== Infinity) {
        // 仅在已加载预约的下一个时间边界更新本地按钮，不发网络请求。
        this.eligibilityTimer = setTimeout(() => this.refreshReservationEligibility(), Math.min(next - now, 2147483647))
        // Node 测试进程中远期预约的计时器不应阻止进程退出；微信环境通常返回数字。
        if (this.eligibilityTimer && typeof this.eligibilityTimer.unref === 'function') this.eligibilityTimer.unref()
      }
    },
    invalidateLoad() {
      this.requestVersion = (this.requestVersion || 0) + 1
      this.setData({ busy: false })
      wx.stopPullDownRefresh()
    },
    syncOwner(token) {
      const role = session.read() && session.read().user.role
      if (this.ownerToken === token && this.ownerRole === role) return
      this.clearEligibilityTimer()
      this.invalidateLoad()
      this.ownerToken = token
      this.ownerRole = role
      this.appliedFilters = {}
      this.setData({ rows: [], total: null, page: 0, hasMore: true, name: '', statusIndex: 0,scopeIndex:0,error: '', actionBusy: false })
    },
    onPullDownRefresh() { if (session.requireLogin()) return this.load(true); wx.stopPullDownRefresh() },
    onReachBottom() { return this.load(false) },
    search() { return this.load(true) },
    filter(e) { this.setData({ statusIndex: Number(e.detail.value) }); return this.load(true) },
    async load(reset) {
      if (this.active === false || !session.requireLogin()) return
      const token = session.read().token, role = session.read().user.role
      if (this.ownerToken !== token || this.ownerRole !== role) { this.syncOwner(token); reset = true }
      if (!reset && (this.data.busy || !this.data.hasMore)) return
      const version = this.requestVersion = (this.requestVersion || 0) + 1
      const isCurrent = () => this.active !== false && this.requestVersion === version
        && session.read() && session.read().token === token && session.read().user.role === role
      if (reset) {
        this.clearEligibilityTimer()
        this.appliedFilters = {}
        if (kind === 'equipment' && this.data.name.trim()) this.appliedFilters.name = this.data.name.trim()
        if (this.data.statusIndex > 0) this.appliedFilters.status = this.data.statusIndex - 1
        this.setData({ rows: [], total: null, page: 0, hasMore: true })
      }
      this.setData({ busy: true, error: '' })
      const current = reset ? 1 : this.data.page + 1
      try {
        const query = { ...this.appliedFilters, current, size: 10 }
        const method = session.manager() && kind === 'reservations' ? 'reservationPage' : session.manager() && kind === 'repairs' && this.data.scopeIndex === 0 ? 'repairPage' : kind
        const result = await api[method](query)
        if (!isCurrent()) return
        if (!result || !Array.isArray(result.records) || !Number.isFinite(Number(result.total)) || Number(result.total) < 0) {
          throw new Error('服务器返回的列表格式错误，请重试')
        }
        const now = Date.now()
        const rows = result.records.map(item => ({ ...item, statusText: labels[item.status] || '未知状态',
          canHandle: session.manager() && this.data.scopeIndex === 0 && (item.status === 0 || session.read().user.role === 'admin' || String(item.handlerId) === String(session.read().user.id)),
          ...reservationEligibility(item, now, !session.manager() && kind === 'reservations')
        }))
        this.setData({ rows: reset ? rows : this.data.rows.concat(rows), total: Number(result.total), page: current, hasMore: current * 10 < result.total })
        this.refreshReservationEligibility()
      } catch (e) { if (isCurrent()) ui.error.call(this, e) }
      finally { if (isCurrent()) { this.setData({ busy: false }); wx.stopPullDownRefresh() } }
    },
    chip(e) { this.setData({ statusIndex: Number(e.currentTarget.dataset.index) }); return this.load(true) },
    browse() { tabs.open('equipment', null) },
    scope(e){this.setData({scopeIndex:Number(e.detail.value)});return this.load(true)},
    more() { return this.load(false) },
    apply(e) { wx.navigateTo({ url: '/pages/apply/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
    report(e) { wx.navigateTo({ url: '/pages/report/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
    detail(e) { wx.navigateTo({url: '/pages/reservation-detail/index?id='+encodeURIComponent(e.currentTarget.dataset.id)}) },
    records(e) { wx.navigateTo({ url: '/pages/records/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
    manage(e) {
      if(!session.manager())return
      const row=this.data.rows.find(r=>String(r.id)===String(e.currentTarget.dataset.id));if(!row)return
      wx.setStorageSync('lab_manage_context',{token:session.read().token,kind,row})
      wx.navigateTo({url:'/pages/manage/index?kind='+kind+'&id='+encodeURIComponent(row.id)+'&action='+encodeURIComponent(e.currentTarget.dataset.action)})
    },
    async action(e) {
      if (this.data.actionBusy || !session.requireStudent()) return
      const token = session.read().token
      const isCurrent = () => this.active !== false && session.read() && session.read().token === token
      const { id, action } = e.currentTarget.dataset
      if (!['cancel', 'signIn'].includes(action)) return
      this.setData({ actionBusy: true, error: '' })
      try {
        if (!await ui.confirm(action === 'cancel' ? '确定取消这次预约？' : '确认已到达实验室并签到？')) return
        if (!isCurrent()) return
        await api[action](id)
        if (!isCurrent()) return
        ui.success('操作成功'); await this.load(true)
      } catch (err) { if (isCurrent()) ui.error.call(this, err) }
      finally { if (this.ownerToken === token) this.setData({ actionBusy: false }) }
    },
    async preview(e) {
      try { const path = await api.image(e.currentTarget.dataset.path); wx.previewImage({ urls: [path] }) }
      catch (err) { ui.error.call(this, err) }
    }
  }
}
