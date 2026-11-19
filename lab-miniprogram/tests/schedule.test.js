const {test}=require('node:test'), assert=require('node:assert/strict')
let definition
const storage=new Map()
global.wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k)}
global.Page=d=>definition=d
const session=require('../utils/session'),api=require('../api/index')
require('../pages/apply/index')
const {networkMessage}=require('../utils/network-error')
function page(){session.save({token:'A',expiresIn:3600,user:{role:'student'}});return {...definition,active:true,formToken:'A',id:1,data:{...definition.data,scheduleDate:'2026-10-09'},setData(v){Object.assign(this.data,v)}}}
test('date switching ignores older schedule results',async()=>{
 const p=page(),pending=[];api.schedule=()=>new Promise(resolve=>pending.push(resolve))
 const first=p.loadSchedule();p.data.scheduleDate='2026-10-10';const second=p.loadSchedule()
 pending[1]({date:'2026-10-10',slots:[]});await second
 pending[0]({date:'2026-10-09',slots:[]});await first
 assert.equal(p.data.schedule.date,'2026-10-10');assert.equal(p.data.scheduleLoading,false)
})
test('unload or account switch discards schedule result',async()=>{
 for(const action of ['unload','account']){const p=page();let resolve;api.schedule=()=>new Promise(r=>resolve=r);const task=p.loadSchedule()
 if(action==='unload')p.onUnload();else session.save({token:'B',expiresIn:3600,user:{role:'student'}})
 resolve({slots:[]});await task;assert.equal(p.data.schedule,null)}
})
test('network failures explain domain timeout TLS and offline without echoing raw input',()=>{
 assert.match(networkMessage({errMsg:'request:fail url not in domain list'},'fallback'),/域名/)
 assert.match(networkMessage({errMsg:'request:fail timeout'},'fallback'),/超时/)
 assert.match(networkMessage({errMsg:'request:fail SSL handshake'},'fallback'),/证书/)
 assert.match(networkMessage({errMsg:'request:fail network is down'},'fallback'),/断开/)
 assert.equal(networkMessage({errMsg:'secret-token=123'},'fallback'),'fallback')
})
