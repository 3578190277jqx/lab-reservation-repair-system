const {test}=require('node:test'),assert=require('node:assert/strict')
let saved,definition,modal,calls
const storage=new Map()
global.wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),switchTab(){},reLaunch(){},navigateBack(){},showToast(){},showModal:o=>o.success({confirm:true}),stopPullDownRefresh(){}}
global.Page=d=>definition=d
const session=require('../utils/session'),api=require('../api/index'),listPage=require('../utils/list-page')
function login(token='A',role='admin'){session.save({token,expiresIn:3600,user:{id:1,role,username:'tester'}})}
function instance(d){return {...d,data:structuredClone(d.data),setData(v){Object.assign(this.data,v)}}}
require('../pages/manage/index');const manager=definition
api.repairDetail=api.reservationDetail=async()=>({status:0})
test('hidden manager request releases busy and accepts refreshed next status',async()=>{login();const p=instance(manager);p.onLoad({id:'1',action:'accept'});p.onShow();let resolve;api.accept=()=>new Promise(r=>resolve=r);const pending=p.submit();await Promise.resolve();p.onHide();resolve({status:1});await pending;assert.equal(p.data.busy,false);api.repairDetail=async()=>({status:1});p.onShow();await Promise.resolve();assert.equal(p.data.stale,true)})
require('../pages/profile/index');const profile=definition
test('old logout confirmation never revokes newly logged in account',async()=>{login();calls=0;wx.showModal=o=>modal=o.success;api.logout=async()=>calls++;const p=instance(profile);const task=p.logout();login('B','student');modal({confirm:true});await task;assert.equal(calls,0);assert.equal(session.read().token,'B')})
test('offline logout clears local session without requiring server response',async()=>{login();wx.showModal=o=>o.success({confirm:true});api.logout=async()=>{throw new Error('offline')};const p=instance(profile);await p.logout();assert.equal(session.read(),null)})
test('manager personal scope calls my endpoint and removes maintenance actions',async()=>{login('A','lab_admin');const p=instance(listPage('repairs'));api.repairs=async()=>({records:[{id:1,status:0}],total:1});p.active=true;p.syncOwner(session.read().token);p.setData({scopeIndex:1});await p.load(true);assert.equal(p.data.rows[0].canHandle,false)})
