const {test}=require('node:test'),assert=require('node:assert/strict')
let saved, definition, navigation=0
 global.wx={getStorageSync:()=>saved,setStorageSync:(_,v)=>saved=v,removeStorageSync:()=>saved=null,reLaunch(){},switchTab(){navigation++},navigateBack(){navigation++},stopPullDownRefresh(){},showToast(){},showModal:o=>o.success({confirm:true})}
const session=require('../utils/session'),api=require('../api/index'),listPage=require('../utils/list-page')
function login(role,id=2){session.save({token:role+id,expiresIn:3600,user:{role,id}})}
function instance(d){return {...d,data:structuredClone(d.data),setData(v){Object.assign(this.data,v)}}}
test('manager lists use scoped endpoints and never expose student actions',async()=>{
 login('lab_admin');let used=''
 api.reservationPage=async()=>{used='managed';return {records:[{id:1,status:0,startTime:'2099-01-01 09:00:00'}],total:1}}
 const p=instance(listPage('reservations'));await p.onShow()
 assert.equal(used,'managed');assert.equal(p.data.manager,true);assert.equal(p.data.rows[0].canCancel,false)
})
test('repair continuation visible only to assigned handler or super admin',async()=>{
 api.repairPage=async()=>({records:[{id:1,status:0},{id:2,status:1,handlerId:2},{id:3,status:2,handlerId:3}],total:3})
 login('lab_admin');let p=instance(listPage('repairs'));await p.onShow()
 assert.deepEqual(p.data.rows.map(r=>r.canHandle),[true,true,false])
 login('admin');p=instance(listPage('repairs'));await p.onShow();assert.ok(p.data.rows.every(r=>r.canHandle))
})
global.Page=d=>definition=d
require('../pages/manage/index')
api.reservationDetail=api.repairDetail=async()=>({id:1,status:0})
function manage(action){api.reservationDetail=api.repairDetail=async()=>({id:1,status:({audit:0,accept:0,start:1,complete:2})[action]});const p=instance(definition);p.onLoad({id:'1',action});p.onShow();return p}
test('reject requires reason; completion validates cost before calling backend',async()=>{
 login('admin');let calls=0;api.audit=api.complete=async()=>calls++
 const p=manage('audit');p.setData({approved:false});await p.submit();assert.match(p.data.error,/说明/)
 const q=manage('complete');q.setData({content:'已更换配件',repairCost:'-1'});await q.submit();assert.match(q.data.error,/费用/);assert.equal(calls,0)
})
test('audit and completion send the existing backend contract',async()=>{
 login('admin');let received
 api.audit=async d=>received=d
 const p=manage('audit');p.setData({approved:false,content:' 时间不合适 '});await p.submit()
 assert.deepEqual(received,{reservationId:'1',approved:false,remark:'时间不合适'})
 api.complete=async d=>received=d
 const q=manage('complete');q.setData({content:'修复完成',repairCost:'12.50'});await q.submit()
 assert.deepEqual(received,{repairOrderId:'1',content:'修复完成',repairCost:12.5})
})
test('account switch while confirmation is open prevents manager submission',async()=>{
 login('admin');let confirm,calls=0;wx.showModal=o=>confirm=o.success;api.accept=async()=>calls++
 const p=manage('accept'),pending=p.submit();login('student');confirm({confirm:true});await pending;assert.equal(calls,0)
})
