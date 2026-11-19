const api=require('../api/index'),session=require('./session'),ui=require('./ui')
const {reservationTimeline}=require('./reservation-detail')
// 每次显示、隐藏和身份变化均丢弃旧响应，防止账号间残留详情。
module.exports = kind => ({
 data:{row:null,rows:[],busy:false,error:'',manager:false},
 onLoad(options){this.id=options.id},
 onShow(){this.active=true;return this.load()},
 onHide(){this.active=false;this.invalidate()},onUnload(){this.active=false;this.invalidate()},
 invalidate(){this.version=(this.version||0)+1;this.setData({row:null,rows:[],busy:false,error:''})},
 onPullDownRefresh(){return this.load()},
 async load(){
  this.invalidate();if(!this.active||!session.requireLogin()){wx.stopPullDownRefresh();return}
  if(!/^[1-9]\d*$/.test(String(this.id))){this.setData({error:'记录编号无效'});wx.stopPullDownRefresh();return}
  const v=this.version,owner=session.read(),current=()=>this.active&&v===this.version&&session.read()&&session.read().token===owner.token&&session.read().user.role===owner.user.role
  this.setData({busy:true,manager:session.manager()})
  try{
   const row=await (kind==='reservation'?api.reservationDetail(this.id):api.repairDetail(this.id))
   if(!current())return
   const rows=kind==='reservation'?reservationTimeline(row):await api.records(this.id)
   if(!current())return
   if(!Array.isArray(rows))throw new Error('详情格式异常，请重试')
   this.setData({row:{...row,statusText:(kind==='reservation'?ui.reservationStatus:ui.repairStatus)[row.status]},rows:kind==='reservation'?rows:rows.map(r=>({title:ui.repairStatus[r.toStatus]||'处理记录',time:r.operationTime,content:r.content,repairCost:r.repairCost}))})
  }catch(e){if(current())ui.error.call(this,e)}finally{if(current()){this.setData({busy:false});wx.stopPullDownRefresh()}}
 }
})
