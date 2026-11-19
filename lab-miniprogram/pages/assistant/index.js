const { request } = require('../../utils/request')
const session = require('../../utils/session')
const { assistantAction, assistantShortcuts, assistantPlan } = require('../../utils/assistant-actions')
const { buildAssistantHistory, assistantModes, readAssistantAnswer } = require('../../utils/assistant-conversation')
const tabs = require('../../utils/tab-route')

Page({
  data: { question: '', messages: [], busy: false, error: '', enabled: null, pending: null, shortcuts: [],
    suggestions: ['我要预约设备', '怎么预约和签到？', '设备已有预约，还能申请吗？', '帮我整理报修描述：设备开机后无显示，今天首次出现。'] },
  onShow() {
    this.active = true; this.reset()
    if (session.requireLogin()) {
      this.owner = session.read()
      this.setData({ shortcuts: assistantShortcuts(this.owner.user.role) }); this.options()
    }
  },
  onHide() { this.active = false; this.reset() },
  onUnload() { this.active = false; this.reset() },
  cancelNavigation() { clearInterval(this.timer); this.timer = null; this.setData({ pending: null }) },
  reset() {
    this.version = (this.version || 0) + 1; this.retryContext = null; this.cancelNavigation()
    this.setData({ question: '', messages: [], busy: false, error: '', enabled: null })
  },
  clear() { this.reset(); if (this.ensureSession()) this.options() },
  input(e) { this.cancelNavigation(); this.setData({ question: e.detail.value }) },
  ensureSession() {
    if (!this.active || !session.requireLogin()) { this.reset(); return false }
    const s = session.read()
    if (this.owner && (s.token !== this.owner.token || s.user.role !== this.owner.user.role)) {
      this.reset(); this.owner = s
      this.setData({ shortcuts: assistantShortcuts(s.user.role) })
      return false
    }
    this.owner = s; return true
  },
  current(v, token, role) {
    if (!this.active || v !== this.version) return false
    const s = session.read()
    if (!s || s.token !== token || s.user.role !== role) { this.reset(); return false }
    return true
  },
  async options() {
    const v = this.version, s = session.read(); if (!s) return
    try { const r = await request('/assistant/options'); if (this.current(v, s.token, s.user.role)) this.setData({ enabled: r.enabled }) } catch {}
  },
  quick(e) { this.setData({ question: this.data.suggestions[e.currentTarget.dataset.index] }); return this.ask() },
  go(e) {
    const code = typeof e === 'string' ? e : e.currentTarget.dataset.code
    this.cancelNavigation(); if (!this.ensureSession()) return
    const action = assistantAction(code, session.read().user.role); if (!action) return
    const v = this.version, s = session.read()
    this.setData({ error: '' })
    // 页面确实隐藏后onHide才丢弃回答；跳转失败仍可以继续本轮问答。
    const fail = () => { if (this.current(v, s.token, s.user.role)) this.setData({ error: '页面暂时无法打开，请重试' }) }
    const kind = action.mini.split('/')[2]
    if (['equipment', 'reservations', 'repairs'].includes(kind)) tabs.open(kind, null, fail)
    else if (action.tab) wx.switchTab({ url: action.mini, fail })
    else wx.navigateTo({ url: action.mini, fail })
  },
  schedule(action) {
    this.cancelNavigation(); const v = this.version, s = session.read(); if (!s) return
    this.setData({ pending: { ...action, seconds: 3 } })
    this.timer = setInterval(() => {
      if (!this.current(v, s.token, s.user.role) || !this.data.pending) return this.cancelNavigation()
      const seconds = this.data.pending.seconds - 1
      if (seconds <= 0) this.go(action.code)
      else this.setData({ pending: { ...this.data.pending, seconds } })
    }, 1000)
  },
  append(item) {
    this.setData({ messages: [...this.data.messages.map(m => ({ ...m, retryable: false })), item].slice(-10) })
  },
  update(id, values) {
    this.setData({ messages: this.data.messages.map(m => m.id === id ? { ...m, ...values } : m) })
  },
  async ask() {
    if (this.data.busy || !this.ensureSession()) return
    const question = this.data.question.trim()
    if (!question || question.length > 1000) return this.setData({ error: '请输入1至1000字的问题' })
    this.cancelNavigation(); this.retryContext = null; this.setData({ error: '' })
    const plan = assistantPlan(question, session.read().user.role), id = this.nextId = (this.nextId || 0) + 1
    if (plan.auto) {
      const hint = (plan.auto.code === 'report' || (plan.auto.code === 'reserve' && session.read().user.role === 'student'))
        ? '请在设备页先选择设备，再点击预约或故障报修。' : plan.auto.hint
      this.append({ id, question, answer: hint, mode: 'navigation', modeText: assistantModes.navigation, actions: plan.actions })
      this.setData({ question: '' }); this.schedule({ ...plan.auto, hint }); return
    }
    const context = { id, question, history: buildAssistantHistory(this.data.messages) }
    this.append({ id, question, answer: '', mode: 'pending', modeText: '正在回答…', actions: plan.actions, loading: true })
    await this.send(context, false)
  },
  async send(context, preserveDraft) {
    const v = ++this.version, s = session.read()
    this.setData({ busy: true, error: '' }); this.cancelNavigation()
    this.update(context.id, { retryable: false, loading: true, modeText: '正在回答…' })
    try {
      const r = await request('/assistant/ask', 'POST', { question: context.question, history: context.history }, false, 20000)
      if (!this.current(v, s.token, s.user.role)) return
      const answer = readAssistantAnswer(r)
      this.update(context.id, { ...answer, modeText: assistantModes[answer.mode], loading: false })
      this.retryContext = answer.retryable ? context : null
      if (!preserveDraft && this.data.question.trim() === context.question) this.setData({ question: '' })
    } catch (e) {
      if (!this.current(v, s.token, s.user.role)) return
      this.update(context.id, { mode: 'error', modeText: assistantModes.error, answer: e.message || '发送失败，请稍后重试', retryable: true, loading: false })
      this.retryContext = context
    } finally { if (this.current(v, s.token, s.user.role)) this.setData({ busy: false }) }
  },
  async retry() {
    if (this.data.busy || !this.ensureSession() || !this.retryContext) return
    const last = this.data.messages[this.data.messages.length - 1]
    if (!last || !last.retryable || last.id !== this.retryContext.id) return
    await this.send(this.retryContext, true)
  },
  copy(e) {
    if (!this.ensureSession()) return
    const id = Number(e.currentTarget.dataset.id), item = this.data.messages.find(m => m.id === id)
    if (!item || !item.answer || item.loading || item.mode === 'error') return
    const v = this.version, s = session.read()
    wx.setClipboardData({ data: item.answer,
      success: () => { if (this.current(v, s.token, s.user.role)) wx.showToast({ title: '回答已复制', icon: 'success' }) },
      fail: () => { if (this.current(v, s.token, s.user.role)) this.setData({ error: '复制失败，请长按回答手动复制' }) },
    })
  },
})
