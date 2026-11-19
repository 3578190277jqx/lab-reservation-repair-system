const api=require('../../api/index'),ui=require('../../utils/ui'),verify=require('../../utils/verification')
Page({
 data:{username:'',email:'',emailCode:'',newPassword:'',confirmPassword:'',busy:false,codeBusy:false,cooldown:0,error:''},input:ui.input,
 onLoad(){this.codePurpose='recover'},onShow:verify.show,onHide:verify.hide,onUnload:verify.unload,sendCode:verify.sendCode,
 async submit(){
  if(this.data.busy)return
  const d=this.data
  if(!/^[A-Za-z0-9_]{4,32}$/.test(d.username.trim())||! /^[A-Za-z0-9._%+-]+@qq\.com$/i.test(d.email.trim())||! /^[0-9]{6}$/.test(d.emailCode))return this.setData({error:'请填写用户名、已验证QQ邮箱和验证码'})
  if(d.newPassword.length<8||d.newPassword.length>64||unescape(encodeURIComponent(d.newPassword)).length>72||d.newPassword!==d.confirmPassword)return this.setData({error:'请填写一致的8–64位新密码（UTF-8不超过72字节）'})
  this.setData({busy:true,error:''})
  try{await api.resetPassword({username:d.username.trim(),channel:'email',target:d.email.trim(),code:d.emailCode,newPassword:d.newPassword});this.setData({newPassword:'',confirmPassword:''});if(this.active){wx.reLaunch({url:'/pages/login/index'});ui.success('密码已重置')}}catch(e){if(this.active)ui.error.call(this,e)}finally{if(!this.unloaded)this.setData({busy:false})}
 }
})
