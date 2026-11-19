<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { register } from '@/api/user'
import VerificationCodeInput from '@/components/VerificationCodeInput.vue'

const router = useRouter()
const formRef = ref()
const busy = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '', realName: '', studentNo: '', phone: '', email: '', emailCode: '' })
const confirmPassword = ref('')
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { pattern: /^[a-zA-Z0-9_]{4,32}$/, message: '使用4–32位字母、数字或下划线', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 8, max: 64, message: '密码长度须为8–64个字符', trigger: 'blur' }, { validator: (_rule, value, done) => new TextEncoder().encode(value).length > 72 ? done(new Error('密码的UTF-8编码不能超过72字节')) : done(), trigger: 'blur' }],
  realName: [{ required: true, whitespace: true, message: '请输入真实姓名', trigger: 'blur' }, { max: 64, message: '真实姓名不能超过64个字符', trigger: 'blur' }],
  studentNo: [{ required: true, whitespace: true, message: '请输入学号', trigger: 'blur' }, { max: 32, message: '学号不能超过32个字符', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '请输入11位手机号', trigger: 'blur' }],
  emailCode: [{required:true,pattern:/^[0-9]{6}$/,message:'请输入6位邮箱验证码',trigger:'blur'}],
  email: [{ required:true, pattern:/^[A-Za-z0-9._%+-]+@qq\.com$/i,message:'请输入QQ邮箱',trigger:'blur' }, { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }, { max: 128, message: '邮箱不能超过128个字符', trigger: 'blur' }],
}
async function submit() {
  if (busy.value || !(await formRef.value.validate().catch(() => false))) return
  if (form.password !== confirmPassword.value) { error.value = '两次输入的密码不一致，请重新确认。'; return }
  busy.value = true; error.value = ''
  try { await register({ ...form, username: form.username.trim(), realName: form.realName.trim(), studentNo: form.studentNo.trim(), phone: form.phone.trim(), email: form.email.trim() }); form.password = ''; confirmPassword.value = ''; ElMessage.success('注册成功，请使用新账号登录'); await router.replace('/login') } catch (err) { error.value = err.response?.data?.message || err.message || '注册未成功，请稍后重试。' } finally { busy.value = false }
}
onBeforeUnmount(() => { form.password = ''; confirmPassword.value = '' })
</script>

<template>
  <div class="register-page"><div class="register-top"><RouterLink to="/login" class="inline-back"><el-icon><ArrowLeft /></el-icon> 返回登录</RouterLink><span class="small-brand">知行实验室</span></div>
    <div class="register-shell"><aside class="register-story"><span class="eyebrow">START YOUR EXPLORATION</span><h1>你的探索，<br>从这里开始。</h1><p>创建学生账号，连接身边的实验资源。</p><div class="register-benefit"><el-icon><CircleCheck /></el-icon><span><strong>发现可用资源</strong><small>查看实验室与设备信息</small></span></div><div class="register-benefit"><el-icon><CircleCheck /></el-icon><span><strong>安排实验计划</strong><small>统一查看个人预约记录</small></span></div><div class="register-benefit"><el-icon><CircleCheck /></el-icon><span><strong>跟进设备报修</strong><small>让问题处理过程清晰可见</small></span></div><p class="register-note">实验室管理员和系统管理员账号请联系平台管理人员开通。</p></aside>
      <section class="register-form"><span class="section-kicker">加入工作空间</span><h2>学生账号注册</h2><p class="auth-intro">请填写真实信息，便于实验室确认你的身份。</p><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
          <div class="form-two-columns"><el-form-item label="真实姓名" prop="realName"><el-input v-model="form.realName" placeholder="请输入真实姓名" autocomplete="name" maxlength="64" :disabled="busy" /></el-form-item><el-form-item label="学号" prop="studentNo"><el-input v-model="form.studentNo" placeholder="请输入学号" maxlength="32" :disabled="busy" /></el-form-item></div>
          <el-form-item label="用户名" prop="username"><el-input v-model="form.username" placeholder="4–32位字母、数字或下划线" autocomplete="username" maxlength="32" :disabled="busy" /></el-form-item>
          <div class="form-two-columns"><el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password placeholder="8–64个字符" autocomplete="new-password" :disabled="busy" /></el-form-item><el-form-item label="确认密码" :error="confirmPassword && form.password !== confirmPassword ? '两次密码不一致' : ''"><el-input v-model="confirmPassword" type="password" show-password placeholder="再次输入密码" autocomplete="new-password" :disabled="busy" /></el-form-item></div>
          <div class="form-two-columns"><el-form-item label="手机号（选填，短信找回待开放）" prop="phone"><el-input v-model="form.phone" placeholder="便于联系" autocomplete="tel" maxlength="20" :disabled="busy" /></el-form-item><el-form-item label="QQ邮箱" prop="email"><el-input v-model="form.email" placeholder="你的QQ邮箱@qq.com" autocomplete="email" maxlength="128" :disabled="busy" /></el-form-item></div>
          <el-form-item label="QQ邮箱验证码" prop="emailCode"><VerificationCodeInput v-model="form.emailCode" purpose="register" :username="form.username" :target="form.email" :disabled="busy"/></el-form-item>
          <el-button class="auth-submit" type="primary" native-type="submit" :loading="busy">创建账号 <el-icon v-if="!busy"><ArrowRight /></el-icon></el-button>
        </el-form><p class="auth-help">已有账号？<RouterLink to="/login">直接登录</RouterLink></p>
      </section>
    </div>
  </div>
</template>
