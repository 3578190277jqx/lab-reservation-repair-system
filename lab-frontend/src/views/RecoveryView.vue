<script setup>
import {reactive,ref,onBeforeUnmount} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import VerificationCodeInput from '@/components/VerificationCodeInput.vue'
import {resetPassword} from '@/api/recovery'
const router=useRouter(),busy=ref(false),error=ref('')
const form=reactive({username:'',target:'',code:'',newPassword:'',confirmPassword:''})
async function submit(){
 if(busy.value)return
 if(!/^[A-Za-z0-9_]{4,32}$/.test(form.username.trim())||! /^[A-Za-z0-9._%+-]+@qq\.com$/i.test(form.target.trim())){error.value='请输入用户名与已验证绑定的QQ邮箱';return}
 if(!/^[0-9]{6}$/.test(form.code)||form.newPassword.length<8||form.newPassword.length>64||new TextEncoder().encode(form.newPassword).length>72||form.newPassword!==form.confirmPassword){error.value='请填写6位验证码及一致的8–64位新密码（UTF-8不超过72字节）';return}
 busy.value=true;error.value=''
 try{await resetPassword({username:form.username.trim(),channel:'email',target:form.target.trim(),code:form.code,newPassword:form.newPassword});form.newPassword='';form.confirmPassword='';ElMessage.success('密码已重置，请重新登录');await router.replace('/login')}
 catch(e){error.value=e.message}finally{busy.value=false}
}
onBeforeUnmount(()=>{form.newPassword='';form.confirmPassword=''})
</script>
<template><div class="page-stack" style="max-width:520px;margin:48px auto;padding:24px"><RouterLink to="/login">返回登录</RouterLink><section class="surface-card" style="padding:28px"><h1>找回密码</h1><p>使用已验证绑定的QQ邮箱。手机号短信找回暂未开放；已有账号请先在个人信息页验证绑定邮箱。</p><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-form label-position="top" @submit.prevent="submit"><el-form-item label="用户名"><el-input v-model="form.username" maxlength="32" :disabled="busy"/></el-form-item><el-form-item label="已绑定QQ邮箱"><el-input v-model="form.target" maxlength="128" :disabled="busy"/></el-form-item><el-form-item label="邮箱验证码"><VerificationCodeInput v-model="form.code" purpose="recover" :username="form.username" :target="form.target" :disabled="busy"/></el-form-item><el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" show-password maxlength="64" :disabled="busy"/></el-form-item><el-form-item label="确认新密码"><el-input v-model="form.confirmPassword" type="password" show-password maxlength="64" :disabled="busy"/></el-form-item><el-button type="primary" native-type="submit" :loading="busy">重置密码</el-button></el-form></section></div></template>
