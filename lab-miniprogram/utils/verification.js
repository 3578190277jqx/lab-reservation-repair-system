const api=require('../api/index'),session=require('./session'),ui=require('./ui')
function username(p){return p.data.username || (p.data.user && p.data.user.username) || ''}
function remaining(p){return p.cooldownTarget===(p.data.email||'').trim().toLowerCase()?Math.max(0,Math.ceil(((p.cooldownUntil||0)-Date.now())/1000)):0}
function show(){this.active=true;this.setData({codeBusy:!!this.codePending,cooldown:remaining(this)})}
function hide(){this.active=false}
function unload(){this.active=false;this.unloaded=true;clearInterval(this.codeTimer)}
async function sendCode(){
 if(this.codePending || remaining(this)>0)return
 const name=username(this).trim(),target=(this.data.email||'').trim(),token=session.read()&&session.read().token
 if(!/^[A-Za-z0-9_]{4,32}$/.test(name)||! /^[A-Za-z0-9._%+-]+@qq\.com$/i.test(target))return this.setData({error:'请先填写用户名和QQ邮箱'})
 const current=()=>!this.unloaded&&this.active!==false&&username(this).trim()===name&&(this.data.email||'').trim()===target&&(this.codePurpose!=='bind'||session.read()&&session.read().token===token)
 this.codePending=true;this.setData({codeBusy:true,error:''})
 try{
  await api.sendCode({purpose:this.codePurpose,username:name,channel:'email',target})
  this.cooldownTarget=target.toLowerCase();this.cooldownUntil=Date.now()+60000
  if(current())ui.success('请查收邮箱')
  clearInterval(this.codeTimer)
  if(!this.unloaded)this.codeTimer=setInterval(()=>{if(this.active&&!this.unloaded)this.setData({cooldown:remaining(this)});if(Date.now()>=this.cooldownUntil)clearInterval(this.codeTimer)},1000)
 }catch(e){if(current())ui.error.call(this,e)}finally{
  this.codePending=false
  // Releasing the request state is independent from the edited recipient/context.
  if(!this.unloaded)this.setData({codeBusy:false,cooldown:remaining(this)})
 }
}
module.exports={show,hide,unload,sendCode}
