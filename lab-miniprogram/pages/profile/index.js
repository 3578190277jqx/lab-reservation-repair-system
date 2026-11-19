const appearance = require('../../utils/appearance')
const api=require('../../api/index'),session=require('../../utils/session'),ui=require('../../utils/ui'),verify=require('../../utils/verification')
Page({
  assistant(){wx.navigateTo({url:'/pages/assistant/index'})},
 data:{user:null,manager:false,error:'',busy:false,email:'',emailCode:'',currentPassword:'',codeBusy:false,cooldown:0},
 input(e){if(e.currentTarget.dataset.field==='email'){this.emailDirty=true;this.emailRevision=(this.emailRevision||0)+1}ui.input.call(this,e)},
 onLoad(){this.codePurpose='bind';this.emailDirty=false;this.emailRevision=0;this.profileRequestId=0},
 onShow(){verify.show.call(this);if(!session.requireLogin()){this.ownerToken=null;this.emailDirty=false;this.profileRequestId=(this.profileRequestId||0)+1;this.setData({user:null,email:'',emailCode:'',currentPassword:'',busy:false,error:''});return}this.setData({manager:appearance.apply('profile')});const token=session.read().token;if(this.ownerToken!==token){this.ownerToken=token;this.emailDirty=false;this.emailRevision=(this.emailRevision||0)+1;this.profileRequestId=(this.profileRequestId||0)+1;this.setData({user:null,email:'',emailCode:'',currentPassword:'',busy:false,error:''})}this.load()},onHide:verify.hide,onUnload:verify.unload,sendCode:verify.sendCode,
 async load(){
  const token=session.read()&&session.read().token,requestId=this.profileRequestId=(this.profileRequestId||0)+1
  const current=()=>this.active&&!this.unloaded&&this.profileRequestId===requestId&&session.read()&&session.read().token===token
  try{const user=await api.info();if(!current())return;const cached=session.read();wx.setStorageSync('lab_student_session',{...cached,user});const next={user,error:''};if(!this.emailDirty)next.email=user.email||'';this.setData(next)}catch(e){if(current())ui.error.call(this,e)}
 },
 stats(){wx.navigateTo({url:'/pages/stats/index'})},
 async bind(){
  if(this.data.busy||!session.requireLogin())return
  const token=session.read().token,target=this.data.email.trim(),revision=this.emailRevision;this.setData({busy:true,error:''})
  const current=()=>!this.unloaded&&session.read()&&session.read().token===token
  try{await api.bindContact({channel:'email',target,code:this.data.emailCode,currentPassword:this.data.currentPassword});if(current()){const next={currentPassword:'',emailCode:''};if(this.emailRevision===revision){this.emailDirty=false;next.email=target}this.setData(next);if(this.active){await this.load();if(current()&&this.active)ui.success('邮箱已绑定')}}}catch(e){if(current()&&this.active)ui.error.call(this,e)}finally{if(current())this.setData({busy:false})}
 },
 async logout(){
  if(this.data.busy)return
  const token=session.read()&&session.read().token
  if(!await ui.confirm('确定退出登录？')||!token||!session.read()||session.read().token!==token)return
  this.setData({busy:true,error:''});let revoked=true
  try{await api.logout()}catch(e){revoked=false}
  finally{
   if(session.read()&&session.read().token===token){session.clear();this.ownerToken=null;this.emailDirty=false;this.profileRequestId=(this.profileRequestId||0)+1;this.setData({user:null,email:'',emailCode:'',busy:false,currentPassword:''});wx.reLaunch({url:'/pages/login/index'});if(!revoked)wx.showToast({title:'已退出本地，远端注销待恢复网络',icon:'none'})}
  }
 }
})
