const api=require('../../api/index'),session=require('../../utils/session'),ui=require('../../utils/ui')
Page({
 data:{rows:[],busy:false,error:''},
 onShow(){this.active=true;if(session.requireLogin()&&session.manager())this.load();else if(session.read())wx.switchTab({url:'/pages/equipment/index'})},
 onHide(){this.active=false},onUnload(){this.active=false},
 async load(){
  if(this.data.busy||!session.requireLogin()||!session.manager())return
  this.setData({busy:true,error:''})
  try{const s=await api.overview();if(!this.active)return
   const names={labCount:'实验室数量',equipmentCount:'设备总数',availableEquipmentCount:'可用设备',reservedEquipmentCount:'已预约设备',repairingEquipmentCount:'维修中设备',reservationCount:'最近30天预约',pendingReservationCount:'最近30天待审核预约',repairOrderCount:'最近30天工单',openRepairOrderCount:'最近30天未完成工单',completedRepairOrderCount:'最近30天完成工单'}
   // 旧服务不提供此字段时隐藏，避免把缺失数据显示成0。
   if(s.expiredReservationCount!=null&&Number.isFinite(Number(s.expiredReservationCount)))names.expiredReservationCount='最近30天审核超时预约'
   this.setData({rows:Object.keys(names).map(k=>({name:names[k],value:s[k]===null?'—':s[k]}))})
  }catch(e){if(this.active)ui.error.call(this,e)}finally{this.setData({busy:false})}
 }
})
