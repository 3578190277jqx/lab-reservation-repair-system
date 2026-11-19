<script setup>
import {ref,reactive,computed,watch,onBeforeUnmount} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage} from 'element-plus'
import {useUserStore} from '@/stores/user'
import {getToken} from '@/utils/auth'
import {getLabPage,createLab,updateLab} from '@/api/lab'
import {createEquipment,updateEquipment} from '@/api/equipment'
import {getUserPage,updateUserRole,updateUser} from '@/api/user'
const emit=defineEmits(['success']),route=useRoute(),user=useUserStore()
const visible=ref(false),busy=ref(false),loading=ref(false),error=ref(''),kind=ref(''),mode=ref(''),labs=ref([]),managers=ref([]),id=ref(null)
const form=reactive({});let version=0,token=''
const titles={lab:'实验室',equipment:'设备',user:'用户'}
const title=computed(()=>mode.value==='role'?'分配用户角色':(id.value?'编辑':'新增')+titles[kind.value])
const states=computed(()=>kind.value==='equipment'?[[0,'可用'],[1,'已预约'],[2,'维修中'],[3,'已报废']]:[[1,'正常／启用'],[0,'禁用／停用']])
function current(v=version){return visible.value&&v===version&&token===getToken()}
function close(){if(busy.value)return;version++;visible.value=false;loading.value=false}
async function allPages(fn,params={},valid){
 const rows=[]
 for(let current=1;current<=100;current++){const d=await fn({...params,current,size:100});if(!valid())return [];rows.push(...d.records);if(rows.length>=Number(d.total))return rows}
 throw new Error('基础资料过多，请先缩小管理范围')
}
async function open(resource,row={},operation='edit'){
 if(busy.value)return
 kind.value=resource;mode.value=operation;id.value=row.id||null;token=getToken();const v=++version;visible.value=true;error.value='';loading.value=true
 for(const key of Object.keys(form))delete form[key]
 const defaults=resource==='lab'?{labCode:'',name:'',location:'',adminId:null,contactPhone:'',description:'',status:1}:resource==='equipment'?{labId:null,equipmentCode:'',name:'',model:'',manufacturer:'',status:0,purchaseDate:null,imageUrl:'',description:''}:{realName:'',studentNo:'',phone:'',email:'',status:1,role:'student'}
 Object.assign(form,defaults,...[row].map(item=>Object.fromEntries(Object.keys(defaults).map(k=>[k,item[k]??defaults[k]]))))
 try{
  if(resource==='equipment'){const values=await allPages(getLabPage,user.isAdmin?{}:{adminId:user.user.id},()=>current(v));if(current(v))labs.value=values}
  if(resource==='lab'){const values=await allPages(getUserPage,{status:1},()=>current(v));if(current(v))managers.value=values.filter(u=>['admin','lab_admin'].includes(u.role))}
 }catch(e){if(current(v))error.value=e.message}finally{if(current(v))loading.value=false}
}
function required(keys){for(const k of keys)if(!String(form[k]??'').trim())throw new Error('请填写所有必填字段')}
async function submit(){
 if(busy.value||loading.value||!current())return
 const v=version;error.value=''
 try{
  if(mode.value!=='role')required(kind.value==='lab'?['labCode','name','location']:kind.value==='equipment'?['labId','equipmentCode','name']:['realName'])
  busy.value=true
  if(mode.value==='role')await updateUserRole({userId:id.value,role:form.role})
  else if(kind.value==='user')await updateUser(id.value,{realName:form.realName,studentNo:form.studentNo||null,phone:form.phone||null,email:form.email||null,status:form.status})
  else{
   const data={...form};for(const k of Object.keys(data))if(typeof data[k]==='string')data[k]=data[k].trim()||null
   const fn=kind.value==='lab'?(id.value?updateLab:createLab):(id.value?updateEquipment:createEquipment)
   if(id.value)await fn(id.value,data);else await fn(data)
  }
  if(!current(v))return
  ElMessage.success('保存成功');visible.value=false;emit('success')
  if(kind.value==='user'&&String(id.value)===String(user.user.id))await user.fetchUserInfo({force:true})
 }catch(e){if(current(v))error.value=e.message}finally{if(v===version)busy.value=false}
}
watch(()=>[route.fullPath,user.token,user.role],()=>{version++;visible.value=false;busy.value=false})
onBeforeUnmount(()=>{version++;})
defineExpose({open})
</script>
<template><el-dialog v-model="visible" :title="title" width="600px" :before-close="done=>{if(!busy){close();done()}}" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy"><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-form label-position="top" :disabled="busy || loading" @submit.prevent="submit">
<el-form-item v-if="mode === 'role'" label="角色"><el-select v-model="form.role"><el-option value="student" label="学生"/><el-option value="lab_admin" label="实验室管理员"/><el-option value="admin" label="超级管理员"/></el-select><p>降级前需移交负责实验室与未完成工单。</p></el-form-item>
<template v-else>
<template v-if="kind === 'lab'"><el-form-item label="实验室编码" required><el-input v-model="form.labCode" maxlength="32"/></el-form-item><el-form-item label="名称" required><el-input v-model="form.name" maxlength="100"/></el-form-item><el-form-item label="位置" required><el-input v-model="form.location" maxlength="255"/></el-form-item><el-form-item label="负责人"><el-select v-model="form.adminId" clearable filterable><el-option v-for="m in managers" :key="m.id" :value="m.id" :label="m.realName+' · '+m.username"/></el-select></el-form-item><el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="20"/></el-form-item></template>
<template v-if="kind === 'equipment'"><el-form-item label="所属实验室" required><el-select v-model="form.labId" filterable><el-option v-for="l in labs" :key="l.id" :value="l.id" :label="l.name+' · '+l.labCode"/></el-select></el-form-item><el-form-item label="资产编号" required><el-input v-model="form.equipmentCode" maxlength="32"/></el-form-item><el-form-item label="名称" required><el-input v-model="form.name" maxlength="100"/></el-form-item><el-form-item label="型号"><el-input v-model="form.model" maxlength="100"/></el-form-item><el-form-item label="制造商"><el-input v-model="form.manufacturer" maxlength="100"/></el-form-item><el-form-item label="采购日期"><el-date-picker v-model="form.purchaseDate" value-format="YYYY-MM-DD" type="date"/></el-form-item><p>有未结束预约或未完成工单时，状态/实验室调整由后端保护；故障请走报修流程。</p></template>
<template v-if="kind === 'user'"><el-form-item label="真实姓名" required><el-input v-model="form.realName" maxlength="64"/></el-form-item><el-form-item label="学号"><el-input v-model="form.studentNo" maxlength="32"/></el-form-item><el-form-item label="手机号"><el-input v-model="form.phone" maxlength="11"/></el-form-item><el-form-item label="邮箱"><el-input v-model="form.email" maxlength="128"/></el-form-item><p>更正联系方式后需用户重新验证绑定，才能用于找回密码。</p></template>
<el-form-item label="状态"><el-select v-model="form.status"><el-option v-for="s in states" :key="s[0]" :value="s[0]" :label="s[1]"/></el-select></el-form-item><el-form-item v-if="kind !== 'user'" label="说明"><el-input v-model="form.description" type="textarea" maxlength="1000"/></el-form-item>
</template></el-form><template #footer><el-button :disabled="busy" @click="close">取消</el-button><el-button type="primary" :loading="busy" :disabled="loading" @click="submit">保存</el-button></template></el-dialog></template>
