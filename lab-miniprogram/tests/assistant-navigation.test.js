const {test,beforeEach,afterEach}=require('node:test'),assert=require('node:assert/strict')
const storage=new Map();let navigated=[],timer,requests=0,resolveReply
global.wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),reLaunch(){},switchTab:o=>navigated.push(o.url),navigateTo:o=>navigated.push(o.url)}
const session=require('../utils/session'),transport=require('../utils/request')
transport.request=async path=>path.endsWith('/options')?{enabled:true}:(requests++,new Promise(r=>resolveReply=r))
let definition;global.Page=d=>definition=d;require('../pages/assistant/index')
const login=(role='student',token='A')=>session.save({token,expiresIn:3600,user:{role,id:1}})
const page=()=>({...definition,data:structuredClone(definition.data),setData(v){Object.assign(this.data,v)}})
let originalSet,originalClear
beforeEach(()=>{login();navigated=[];requests=0;timer=null;originalSet=global.setInterval;originalClear=global.clearInterval;global.setInterval=fn=>(timer=fn,1);global.clearInterval=()=>{timer=null}})
afterEach(()=>{global.setInterval=originalSet;global.clearInterval=originalClear})
test('student navigation needs no model and cannot submit a reservation',async()=>{const p=page();p.onShow();p.setData({question:'我要预约设备'});await p.ask();assert.equal(requests,0);assert.equal(p.data.pending.seconds,3);timer();timer();timer();assert.deepEqual(navigated,['/pages/equipment/index']);assert.equal(timer,null)})
test('manager reservation command opens scoped review tab',async()=>{login('lab_admin');const p=page();p.onShow();p.setData({question:'我要预约'});await p.ask();timer();timer();timer();assert.deepEqual(navigated,['/pages/reservations/index'])})
test('cancel, typing, hide, unload, and clear all cancel pending navigation',async()=>{for(const event of ['cancelNavigation','input','onHide','onUnload','clear']){const p=page();p.onShow();p.setData({question:'我要预约'});await p.ask();p[event]({detail:{value:'规则'}});assert.equal(timer,null);assert.equal(p.data.pending,null)}assert.deepEqual(navigated,[])})
test('account or role change blocks delayed navigation',async()=>{for(const role of ['student','admin']){login();const p=page();p.onShow();p.setData({question:'我要预约'});await p.ask();login(role,role==='student'?'B':'A');timer();assert.deepEqual(navigated,[]);assert.equal(timer,null)}})
test('policy answer remains visible and model cannot choose a navigation URL',async()=>{const p=page();p.onShow();p.setData({question:'为什么预约被取消'});const wait=p.ask();resolveReply({mode:'model',answer:'规则说明',actions:[{code:'https://evil.test'}]});await wait;assert.equal(p.data.pending,null);assert.equal(p.data.messages[0].actions[0].code,'reservations');p.go('https://evil.test');assert.deepEqual(navigated,[])})
test('late model reply under changed role is discarded',async()=>{const p=page();p.onShow();p.setData({question:'签到规则'});const wait=p.ask();login('admin');resolveReply({mode:'model',answer:'old answer'});await wait;assert.deepEqual(p.data.messages,[])})
