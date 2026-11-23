const {test}=require('node:test'),assert=require('node:assert/strict')
const storage=new Map();let definition
global.wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),reLaunch(){},switchTab(){},showToast(){}}
global.Page=d=>definition=d
const session=require('../utils/session'),api=require('../api/index')
require('../pages/profile/index');const profile=definition
function login(token='A',id=1){session.save({token,expiresIn:3600,user:{id,role:'student',username:'tester'}})}
function person(email,id=1){return {id,role:'student',username:'tester',email}}
function instance(){const p={...profile,data:structuredClone(profile.data),setData(v){Object.assign(this.data,v)}};p.onLoad();return p}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject}}
function edit(p,field,value){p.input({currentTarget:{dataset:{field}},detail:{value}})}
async function flush(){await new Promise(resolve=>setImmediate(resolve))}

test('a late profile response preserves the edited email draft',async()=>{
 login();const pending=deferred();api.info=()=>pending.promise;const p=instance();p.onShow();edit(p,'email','new@qq.com');pending.resolve(person('old@qq.com'));await flush()
 assert.equal(p.data.email,'new@qq.com');assert.equal(p.data.user.email,'old@qq.com')
})
test('latest same-account profile load wins even when older load finishes last',async()=>{
 login();const first=deferred(),second=deferred();let count=0;api.info=()=>++count===1?first.promise:second.promise;const p=instance();p.onShow();const later=p.load();second.resolve(person('newer@qq.com'));await later;first.resolve(person('older@qq.com'));await flush()
 assert.equal(p.data.user.email,'newer@qq.com');assert.equal(p.data.email,'newer@qq.com');assert.equal(session.read().user.email,'newer@qq.com')
})
test('stale errors do not replace the successful latest request',async()=>{
 login();const first=deferred(),second=deferred();let count=0;api.info=()=>++count===1?first.promise:second.promise;const p=instance();p.onShow();const later=p.load();second.resolve(person('newer@qq.com'));await later;first.reject(new Error('旧请求错误'));await flush();assert.equal(p.data.error,'')
})
test('successful binding refreshes the email baseline and ignores older in-flight profiles',async()=>{
 login();const old=deferred(),latest=deferred();let count=0;api.info=()=>++count===1?old.promise:latest.promise;api.bindContact=async()=>{};const p=instance();p.onShow();edit(p,'email','bound@qq.com');edit(p,'emailCode','123456');edit(p,'currentPassword','password');const task=p.bind();await flush();latest.resolve(person('bound@qq.com'));await task;old.resolve(person('old@qq.com'));await flush()
 assert.equal(p.data.email,'bound@qq.com');assert.equal(p.data.user.email,'bound@qq.com');assert.equal(p.data.emailCode,'');assert.equal(p.data.currentPassword,'')
})
test('edits made during binding remain as a new draft',async()=>{
 login();api.info=async()=>person('old@qq.com');const bind=deferred();api.bindContact=()=>bind.promise;const p=instance();p.onShow();await flush();edit(p,'email','first@qq.com');edit(p,'emailCode','123456');edit(p,'currentPassword','password');const task=p.bind();edit(p,'email','next@qq.com');api.info=async()=>person('first@qq.com');bind.resolve();await task
 assert.equal(p.data.email,'next@qq.com');assert.equal(p.data.user.email,'first@qq.com')
})
test('switching account clears drafts and ignores previous-account responses',async()=>{
 login();const first=deferred(),second=deferred();let count=0;api.info=()=>++count===1?first.promise:second.promise;const p=instance();p.onShow();edit(p,'email','private@qq.com');edit(p,'emailCode','123456');edit(p,'currentPassword','secret');p.onHide();login('B',2);p.onShow()
 assert.equal(p.data.email,'');assert.equal(p.data.emailCode,'');assert.equal(p.data.currentPassword,'');second.resolve(person('account-b@qq.com',2));await flush();first.resolve(person('account-a@qq.com',1));await flush();assert.equal(p.data.email,'account-b@qq.com');assert.equal(p.data.user.id,2);assert.equal(session.read().token,'B')
})
test('an unchanged email form refreshes from the profile',async()=>{
 login();api.info=async()=>person('existing@qq.com');const p=instance();p.onShow();await flush();assert.equal(p.data.email,'existing@qq.com')
})
test('hiding and showing the same account preserves an unfinished email draft',async()=>{
 login();api.info=async()=>person('saved@qq.com');const p=instance();p.onShow();await flush();edit(p,'email','draft@qq.com');p.onHide();p.onShow();await flush();assert.equal(p.data.email,'draft@qq.com');assert.equal(p.data.user.email,'saved@qq.com')
})
test('expired session clears personal information and draft on return',async()=>{
 login();api.info=async()=>person('saved@qq.com');const p=instance();p.onShow();await flush();edit(p,'email','private@qq.com');edit(p,'currentPassword','secret');p.onHide();session.clear();p.onShow();assert.equal(p.data.user,null);assert.equal(p.data.email,'');assert.equal(p.data.currentPassword,'')
})
test('binding completion from old account cannot alter the new account',async()=>{
 login();api.info=async()=>person('account-a@qq.com');const operation=deferred();api.bindContact=()=>operation.promise;const p=instance();p.onShow();await flush();edit(p,'email','bound-a@qq.com');edit(p,'currentPassword','secret');const task=p.bind();p.onHide();login('B',2);api.info=async()=>person('account-b@qq.com',2);p.onShow();await flush();operation.resolve();await task;assert.equal(p.data.user.id,2);assert.equal(p.data.email,'account-b@qq.com');assert.equal(p.data.busy,false)
})
test('logout immediately clears sensitive draft data',async()=>{
 login();api.info=async()=>person('saved@qq.com');api.logout=async()=>{};wx.showModal=o=>o.success({confirm:true});const p=instance();p.onShow();await flush();edit(p,'email','private@qq.com');edit(p,'emailCode','123456');edit(p,'currentPassword','secret');await p.logout();assert.equal(p.data.email,'');assert.equal(p.data.emailCode,'');assert.equal(p.data.currentPassword,'');assert.equal(session.read(),null)
})
