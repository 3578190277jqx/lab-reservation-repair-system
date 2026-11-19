const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const storage = new Map()
let definition, navigated, styled
const tabItems = []
global.wx = {
  getStorageSync: k => storage.get(k), setStorageSync: (k,v) => storage.set(k,v), removeStorageSync: k => storage.delete(k),
  reLaunch() {}, stopPullDownRefresh() {}, switchTab: o => { navigated = o.url }, navigateTo: o => { navigated = o.url },
  setTabBarStyle: o => { styled = o }, setTabBarItem: o => tabItems.push(o), setNavigationBarTitle() {}, setNavigationBarColor() {}
}
global.Page = d => { definition = d }
const api = require('../api/index'), session = require('../utils/session'), workspace = require('../utils/workspace')
const tabs = require('../utils/tab-route'), listPage = require('../utils/list-page'), appearance = require('../utils/appearance')
require('../pages/home/index')
const home = definition
const instance = d => ({ ...d, data: structuredClone(d.data), setData(v) { Object.assign(this.data,v) } })
const login = (role='student', token='A') => session.save({ token, expiresIn:3600, user:{ id:1, role, username:role } })
const result = id => ({ records:[{id,status:0,equipmentId:2}],total:7 })
beforeEach(() => {
  storage.clear(); tabItems.length=0; navigated=''; login()
  api.equipment=api.reservations=api.repairs=api.reservationPage=api.repairPage=async()=>result(1)
  api.overview=async()=>({ equipmentCount:20, availableEquipmentCount:12, repairingEquipmentCount:3 })
})
test('student home calls only personal endpoints with bounded pages', async()=>{
  const calls=[]
  for(const method of ['equipment','reservations','repairs'])api[method]=async q=>{calls.push([method,q]);return result(1)}
  api.overview=api.reservationPage=api.repairPage=()=>assert.fail('student requested manager data')
  const data=await workspace.load(false)
  assert.deepEqual(calls,[['equipment',{current:1,size:4,status:0}],['reservations',{current:1,size:3}],['repairs',{current:1,size:3}]])
  assert.equal(data.reservations.total,7);assert.equal(data.overview,null)
})
test('manager home uses scoped pending queues and independent failure states',async()=>{
  login('lab_admin');const calls=[]
  api.reservationPage=async q=>{calls.push(q);return result(2)}
  api.repairPage=async q=>{calls.push(q);throw new Error('连接超时')}
  api.reservations=api.repairs=()=>assert.fail('manager requested personal data')
  const data=await workspace.load(true)
  assert.deepEqual(calls,[{current:1,size:3,status:0},{current:1,size:3,status:0}])
  assert.equal(data.reservations.total,7);assert.equal(data.repairs,null);assert.equal(data.errors.repairs,'连接超时');assert.equal(data.overview.equipmentCount,20)
})
test('malformed home data is an error rather than a zero count',async()=>{
 api.overview=async()=>({equipmentCount:null});api.reservationPage=async()=>({records:null,total:0})
 const data=await workspace.load(true)
 assert.equal(data.overview,null);assert.equal(data.reservations,null);assert.match(data.errors.overview,/异常/);assert.match(data.errors.reservations,/异常/)
})
test('older refresh cannot overwrite latest dashboard result',async()=>{
 const pending=[];api.reservations=()=>new Promise(resolve=>pending.push(resolve))
 const p=instance(home),old=p.onShow(),fresh=p.load()
 pending[1](result('new'));await fresh;pending[0](result('old'));await old
 assert.equal(p.data.reservations.rows[0].id,'new');assert.equal(p.data.busy,false)
})
test('account change clears old dashboard and discards late responses',async()=>{
 const pending=[];api.reservations=()=>new Promise(resolve=>pending.push(resolve))
 const p=instance(home),old=p.onShow();login('student','B');const fresh=p.onShow()
 assert.equal(p.data.reservations,null)
 pending[0](result('private'));await old;assert.equal(p.data.reservations,null)
 pending[1](result('current'));await fresh;assert.equal(p.data.reservations.rows[0].id,'current')
})
test('role change under the same token rejects manager response',async()=>{
 login('admin');let resolve;api.overview=()=>new Promise(r=>resolve=r)
 const p=instance(home),old=p.onShow();login('student');await p.onShow()
 resolve({equipmentCount:200,availableEquipmentCount:100,repairingEquipmentCount:1});await old
 assert.equal(p.data.manager,false);assert.equal(p.data.overview,null)
})
test('hidden and unloaded dashboards do not accept late writes',async()=>{
 for(const event of ['onHide','onUnload']){
  let resolve;api.reservations=()=>new Promise(r=>resolve=r)
  const p=instance(home),loading=p.onShow();p[event]();p.setData=()=>assert.fail('inactive page write')
  resolve(result(1));await loading
 }
})
test('dashboard status zero links reset old filters and select correct manager endpoint',async()=>{
 login('lab_admin');tabs.open('reservations',0);assert.equal(navigated,'/pages/reservations/index')
 let query;api.reservationPage=async q=>{query=q;return result(1)}
 const p=instance(listPage('reservations'));await p.onShow()
 assert.equal(p.data.statusIndex,1);assert.equal(query.status,0);assert.equal(storage.has('lab_tab_filter'),false)
 tabs.open('reservations',null);await p.onShow();assert.equal(p.data.statusIndex,0);assert.equal(query.status,undefined)
})
test('filter handoff is account-bound role-bound and consumed once',()=>{
 tabs.open('repairs',0);login('student','B');assert.equal(tabs.consume('repairs'),null)
 tabs.open('repairs',0);login('admin','B');assert.equal(tabs.consume('repairs'),null)
 tabs.open('equipment',3);assert.equal(tabs.consume('repairs'),null);assert.equal(tabs.consume('equipment').statusIndex,4);assert.equal(tabs.consume('equipment'),null)
})
test('manager personal report handoff still opens own repair scope',async()=>{
 login('lab_admin');storage.set('lab_repair_scope',{token:'A',scopeIndex:1});let personal=false
 api.repairs=async()=>{personal=true;return result(1)}
 const p=instance(listPage('repairs'));await p.onShow();assert.equal(personal,true);assert.equal(p.data.scopeIndex,1);assert.equal(p.data.rows[0].canHandle,false)
})
test('role appearance changes all native tab labels and colors',()=>{
 login('admin');appearance.apply('home');assert.equal(styled.selectedColor,'#315ee7');assert.equal(tabItems[0].text,'工作台');assert.equal(tabItems[2].text,'审核')
 login('student');appearance.apply('home');assert.equal(styled.selectedColor,'#087f70');assert.equal(tabItems[5].text,'首页');assert.equal(tabItems[7].text,'预约')
})
test('only pending managed rows can navigate to an audit or accept form',async()=>{
 login('lab_admin');const p=instance(home);await p.onShow();p.handle({currentTarget:{dataset:{kind:'reservations',id:1}}});assert.match(navigated,/action=audit/)
 navigated='';login('student');p.handle({currentTarget:{dataset:{kind:'repairs',id:1}}});assert.equal(navigated,'')
})
test('tab assets and registered routes exist, with no more than five tabs',()=>{
 const fs=require('node:fs'),path=require('node:path'),root=path.resolve(__dirname,'..'),app=require('../app.json')
 assert.equal(app.tabBar.list.length,5)
 for(const item of app.tabBar.list){assert.ok(app.pages.includes(item.pagePath));assert.ok(fs.existsSync(path.join(root,item.iconPath)));assert.ok(fs.existsSync(path.join(root,item.selectedIconPath)))}
 for(const page of app.pages)for(const ext of ['js','json','wxml'])assert.ok(fs.existsSync(path.join(root,page+'.'+ext)))
})
