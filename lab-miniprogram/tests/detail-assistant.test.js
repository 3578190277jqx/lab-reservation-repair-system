const {test,beforeEach}=require('node:test'),assert=require('node:assert/strict')
const storage=new Map();global.wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),reLaunch(){},stopPullDownRefresh(){}}
const session=require('../utils/session'),api=require('../api/index'),detail=require('../utils/detail-page')
const page=d=>({...d,data:structuredClone(d.data),setData(v){Object.assign(this.data,v)}})
function login(token){session.save({token,expiresIn:3600,user:{role:'student',id:1}})}
beforeEach(()=>login('A'))
test('old detail responses cannot appear after account switch',async()=>{let resolve;api.reservationDetail=()=>new Promise(r=>resolve=r);const p=page(detail('reservation'));p.onLoad({id:'1'});const request=p.onShow();login('B');resolve({id:1,purpose:'private'});await request;assert.equal(p.data.row,null)})
test('hiding clears detail and a later show reloads',async()=>{api.reservationDetail=async()=>({id:1,status:3,cancelTime:'2026-10-10 09:00:00',cancelReason:'维修'});const p=page(detail('reservation'));p.onLoad({id:'1'});await p.onShow();assert.equal(p.data.rows[0].content,'维修');p.onHide();assert.equal(p.data.row,null);await p.onShow();assert.equal(p.data.row.id,1)})
test('repair records are discarded when page hidden while loading',async()=>{api.repairDetail=async()=>({id:1});let resolve;api.records=()=>new Promise(r=>resolve=r);const p=page(detail('repair'));p.onLoad({id:'1'});const pending=p.onShow();await Promise.resolve();p.onHide();resolve([{content:'private'}]);await pending;assert.deepEqual(p.data.rows,[])})
test('invalid IDs never request backend',async()=>{api.reservationDetail=()=>assert.fail('unexpected request');const p=page(detail('reservation'));p.onLoad({id:'../user'});await p.onShow();assert.equal(p.data.error,'记录编号无效')})
let definition;global.Page=d=>definition=d
// Stub transport before assistant module loads.
const transport=require('../utils/request');let reply
transport.request=async(path)=>path.endsWith('/options')?{enabled:false}:new Promise(r=>reply=r)
require('../pages/assistant/index')
test('assistant clears private questions and ignores late reply after hide',async()=>{const p=page(definition);p.onShow();p.setData({question:'private question'});const pending=p.ask();p.onHide();reply({mode:'model',answer:'private answer'});await pending;assert.equal(p.data.question,'');assert.deepEqual(p.data.messages,[])})
test('assistant distinguishes fixed guide from generated answer',async()=>{const p=page(definition);p.onShow();p.setData({question:'签到规则'});const pending=p.ask();reply({mode:'guide',answer:'固定指南'});await pending;assert.match(p.data.messages[0].modeText,/未启用/);assert.equal(p.data.busy,false)})
