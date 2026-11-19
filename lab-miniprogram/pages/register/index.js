const api = require('../../api/index'), ui = require('../../utils/ui'),verify=require('../../utils/verification')
Page({
  data: { username: '', password: '', confirmPassword: '', realName: '', studentNo: '',email:'',phone:'',emailCode:'',codeBusy:false,cooldown:0,busy: false, error: '' }, input: ui.input,
  onLoad(){this.codePurpose='register'},onShow(){verify.show.call(this);if(this.submitted){wx.navigateBack();ui.success('注册成功，请登录')}},onHide:verify.hide,onUnload:verify.unload,sendCode:verify.sendCode,
  async submit() {
    if (this.data.busy || this.submitted || this.unloaded) return
    const { password, confirmPassword } = this.data
    const username = this.data.username.trim(), realName = this.data.realName.trim(), studentNo = this.data.studentNo.trim()
    if (!/^[A-Za-z0-9_]{4,32}$/.test(username)) return this.setData({ error: '用户名须为4–32位字母、数字或下划线' })
    if (password.length < 8 || password.length > 64 || unescape(encodeURIComponent(password)).length > 72) return this.setData({ error: '密码须为8–64字符且不超过72个UTF-8字节' })
    if (password !== confirmPassword) return this.setData({ error: '两次密码不一致' })
    if (!realName || !studentNo) return this.setData({ error: '请填写姓名和学号' })
    if(!/^[A-Za-z0-9._%+-]+@qq\.com$/i.test(this.data.email.trim())||! /^[0-9]{6}$/.test(this.data.emailCode))return this.setData({error:'请填写QQ邮箱和6位验证码'})
    if(this.data.phone && !/^1[3-9][0-9]{9}$/.test(this.data.phone))return this.setData({error:'请输入11位手机号'})
    this.setData({ busy: true, error: '' })
    try { await api.register({ username, password, realName, studentNo, email:this.data.email.trim(),phone:this.data.phone.trim(),emailCode:this.data.emailCode }); this.submitted=true; if(this.active&&!this.unloaded){wx.navigateBack(); ui.success('注册成功，请登录')} }
    catch (e) { if(this.active&&!this.unloaded)ui.error.call(this, e) } finally { if(!this.unloaded)this.setData({ busy: false }) }
  }
})
