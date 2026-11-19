<script setup>
import {ref,onBeforeUnmount,watch} from 'vue'
import {ElMessage} from 'element-plus'
import {sendVerificationCode} from '@/api/recovery'
import {getToken} from '@/utils/auth'
const props=defineProps({modelValue:String,purpose:String,username:String,target:String,disabled:Boolean})
const emit=defineEmits(['update:modelValue'])
const busy=ref(false),seconds=ref(0),error=ref('');let timer,version=0
watch(()=>[props.username,props.target,props.purpose],()=>{version++;error.value='';emit('update:modelValue','')})
async function send(){
 if(busy.value||seconds.value||props.disabled)return
 if(!/^[A-Za-z0-9_]{4,32}$/.test(props.username?.trim()||'')||! /^[A-Za-z0-9._%+-]+@qq\.com$/i.test(props.target?.trim()||'')){error.value='请先填写有效用户名和QQ邮箱';return}
 const id=version,token=getToken();busy.value=true;error.value=''
 try{
  await sendVerificationCode({purpose:props.purpose,username:props.username.trim(),channel:'email',target:props.target.trim()})
  if(id!==version||(props.purpose==='bind'&&token!==getToken()))return
  ElMessage.success('如账号和邮箱符合要求，验证码将发送至邮箱');seconds.value=60
  clearInterval(timer);timer=setInterval(()=>{if(seconds.value>0)seconds.value--;else clearInterval(timer)},1000)
 }catch(e){if(id===version)error.value=e.message}finally{busy.value=false}
}
onBeforeUnmount(()=>{version++;clearInterval(timer)})
</script>
<template><div style="width:100%"><div style="display:flex;gap:12px"><el-input :model-value="modelValue" @update:model-value="emit('update:modelValue',$event)" maxlength="6" inputmode="numeric" placeholder="6位邮箱验证码" :disabled="disabled"/><el-button @click="send" :loading="busy" :disabled="disabled || seconds>0">{{seconds ? seconds+'秒后重试':'发送验证码'}}</el-button></div><p v-if="error" style="color:#b42318">{{error}}</p></div></template>
