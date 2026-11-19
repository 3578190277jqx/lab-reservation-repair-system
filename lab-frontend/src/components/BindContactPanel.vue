<script setup>
import {ref,watch,onBeforeUnmount} from 'vue'
import {useUserStore} from '@/stores/user'
import {getToken} from '@/utils/auth'
import {bindContact} from '@/api/recovery'
import VerificationCodeInput from './VerificationCodeInput.vue'
const user=useUserStore(),email=ref(user.user?.email||''),code=ref(''),password=ref(''),busy=ref(false),message=ref('')
watch(()=>user.user?.id,()=>{email.value=user.user?.email||'';code.value='';password.value='';message.value=''})
async function submit(){
 if(busy.value)return
 const token=getToken();busy.value=true;message.value=''
 try{await bindContact({channel:'email',target:email.value.trim(),code:code.value,currentPassword:password.value});if(token!==getToken())return;password.value='';code.value='';await user.fetchUserInfo({force:true});message.value='QQ邮箱已验证绑定，可用于找回密码'}catch(e){if(token===getToken())message.value=e.message}finally{busy.value=false}
}
onBeforeUnmount(()=>{password.value=''})
</script>
<template><section class="surface-card" style="padding:24px"><h2>密码找回邮箱</h2><p>{{user.user?.emailVerified === 1 ? '当前邮箱已验证':'已有邮箱尚未验证，请先绑定'}}。手机号短信找回待开放。</p><el-form label-position="top" @submit.prevent="submit"><el-form-item label="QQ邮箱"><el-input v-model="email" :disabled="busy"/></el-form-item><el-form-item label="邮箱验证码"><VerificationCodeInput v-model="code" purpose="bind" :username="user.user?.username" :target="email" :disabled="busy"/></el-form-item><el-form-item label="当前登录密码"><el-input v-model="password" type="password" show-password maxlength="64" :disabled="busy"/></el-form-item><el-button type="primary" native-type="submit" :loading="busy">验证并绑定</el-button></el-form><p v-if="message">{{message}}</p></section></template>
