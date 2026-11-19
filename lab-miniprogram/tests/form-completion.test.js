const {test}=require('node:test'),assert=require('node:assert/strict');let definition,navigations=0;const storage=new Map()
global.wx={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),reLaunch(){navigations++},switchTab(){navigations++},navigateBack(){navigations++},showToast(){}}
global.Page=d=>definition=d
const api=require('../api/index'),session=require('../utils/session'),verify=require('../utils/verification')
function instance(d){return {...d,data:structuredClone(d.data),setData(v){Object.assign(this.data,v)}}}
function login(){session.save({token:'A',expiresIn:3600,user:{id:1,role:'student',username:'tester'}})}
test('edited recipient releases sender busy and has its own cooldown',async()=>{let complete;api.sendCode=()=>new Promise(r=>complete=r);const p=instance({data:{username:'tester',email:'first@qq.com',codeBusy:false,cooldown:0},codePurpose:'register'});verify.show.call(p);const task=verify.sendCode.call(p);p.setData({email:'second@qq.com'});complete();await task;assert.equal(p.data.codeBusy,false);assert.equal(p.data.cooldown,0);verify.unload.call(p)})
require('../pages/register/index');const register=definition
require('../pages/apply/index');const apply=definition
require('../pages/report/index');const report=definition
for(const [name,d,apiName] of [['register',register,'register'],['apply',apply,'apply'],['report',report,'report']])test(name+' completion never navigates after native back/unload',async()=>{
 login();let complete;api[apiName]=()=>new Promise(r=>complete=r);const p=instance(d);p.active=true;p.formToken='A';p.id=1;p.setData({equipment:{id:1},username:'tester',realName:'验收',studentNo:'T1',password:'TestPass@123',confirmPassword:'TestPass@123',email:'tester@qq.com',emailCode:'123456',phone:'',startDate:'2099-01-01',endDate:'2099-01-01',startClock:'09:00',endClock:'10:00',purpose:'验收',description:'验收'});
 const before=navigations,task=p.submit();p.onUnload();complete({id:1});await task;assert.equal(navigations,before)
})

test('resumed manager report success always opens personal scope',()=>{session.save({token:'A',expiresIn:3600,user:{id:1,role:'lab_admin',username:'tester'}});const p=instance(report);p.formToken='A';p.submitted=true;p.onShow();assert.equal(storage.get('lab_repair_scope').scopeIndex,1)})
