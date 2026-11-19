const api = require('../../api/index')
const session = require('../../utils/session')
const ui = require('../../utils/ui')
Page({
  data: { username: '', password: '', busy: false, error: '' }, input: ui.input,
  onShow() { const s = session.read(); if (s && s.expiresAt > Date.now() && s.user && ['student','admin','lab_admin'].includes(s.user.role)) wx.switchTab({ url: '/pages/home/index' }) },
  recover(){wx.navigateTo({url:'/pages/recover/index'})},
  register() { wx.navigateTo({ url: '/pages/register/index' }) },
  async submit() {
    if (this.data.busy) return
    const username = this.data.username.trim(), password = this.data.password
    if (!username || !password) return this.setData({ error: '请填写账号和密码' })
    this.setData({ busy: true, error: '' })
    try {
      const login = await api.login({ username, password })
      session.save(login)
      const user=await api.info();const cached=session.read();if(cached)wx.setStorageSync('lab_student_session',{...cached,user})
      this.setData({ password: '' }); wx.switchTab({ url: '/pages/home/index' })
    } catch (e) { ui.error.call(this, e) } finally { this.setData({ busy: false }) }
  }
})
