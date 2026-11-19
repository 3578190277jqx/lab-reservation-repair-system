const api=require('../../api/index'),session=require('../../utils/session'),ui=require('../../utils/ui')
Page({
 data:{summary:null,stale:false,loading:false,action:'',title:'',approved:true,content:'',repairCost:'0',busy:false,error:''},input:ui.input,
 onLoad(options){
  this.id=options.id
  const c=wx.getStorageSync('lab_manage_context'),s=session.read()
  if(c && c.row && s && c.token===s.token && String(c.row.id)===String(this.id))this.setData({summary:c.row})
  const titles={audit:'审核预约',accept:'受理工单',start:'开始维修',complete:'完成维修'}
  if(!titles[options.action] || !/^\d+$/.test(this.id||'')){this.setData({error:'无效操作'});return}
  this.setData({action:options.action,title:titles[options.action]})
 },
 onShow(){this.active=true;if(!session.requireLogin())return;if(!session.manager())wx.switchTab({url:'/pages/equipment/index'});else this.loadSummary()},
 onHide(){this.active=false},onUnload(){this.active=false;this.unloaded=true},
 async loadSummary(){
  if(this.data.busy||!this.data.action)return
  const token=session.read().token;this.setData({loading:true})
  try{const summary=await (this.data.action==='audit'?api.reservationDetail:api.repairDetail)(this.id);if(this.active&&session.read()&&session.read().token===token)this.setData({summary,stale:summary.status!==({audit:0,accept:0,start:1,complete:2})[this.data.action],...(summary.status!==({audit:0,accept:0,start:1,complete:2})[this.data.action]?{error:'状态已更新，请返回列表后选择下一步'}:{})})}catch(e){if(this.active)ui.error.call(this,e)}finally{if(!this.unloaded&&session.read()&&session.read().token===token)this.setData({loading:false})}
 },
 approval(e){this.setData({approved:e.detail.value==='true'})},
 async submit(){
  if(this.data.stale||this.data.busy||!this.data.action||!session.requireLogin()||!session.manager())return
  const {action,approved,repairCost}=this.data, content=this.data.content.trim()
  if((action==='audit'&&!approved||action==='complete')&&!content)return this.setData({error:'请填写处理说明'})
  if(content.length>(action==='audit'?500:2000))return this.setData({error:'处理说明过长'})
  if(action==='complete'&&!/^(0|[1-9]\d{0,7})(\.\d{1,2})?$/.test(repairCost))return this.setData({error:'维修费用应为非负金额，最多8位整数和2位小数'})
  const token=session.read().token
  const current=()=>this.active&&session.read()&&session.read().token===token
  this.setData({busy:true,error:''})
  try{
   if(!await ui.confirm(action==='accept'?'受理后设备进入维修状态，相关预约将按后端规则取消。确认受理？':'确认提交本次处理？'))return
   if(!current())return
   const data=action==='audit'?{reservationId:this.id,approved,remark:content}:{repairOrderId:this.id,content}
   if(action==='complete')data.repairCost=Number(repairCost)
   await api[action](data)
   if(current()){ui.success('处理成功');wx.navigateBack()}
  }catch(e){if(current())ui.error.call(this,e)}finally{if(!this.unloaded && session.read() && session.read().token===token)this.setData({busy:false})}
 }
})
