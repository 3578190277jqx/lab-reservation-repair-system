<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock, ArrowRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { safeRedirect } from '@/utils/navigation'
import LabScene from '@/components/visual/LabScene.vue'

const user = useUserStore()
const route = useRoute()
const router = useRouter()
const formRef = ref()
const busy = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '' })
const rules = { username: [{ required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' }], password: [{ required: true, message: '请输入密码', trigger: 'blur' }] }
async function submit() {
  if (busy.value || !(await formRef.value.validate().catch(() => false))) return
  busy.value = true; error.value = ''
  try {
    await user.signIn({ username: form.username.trim(), password: form.password })
    form.password = ''
    await router.replace(safeRedirect(route.query.redirect) || '/home')
  } catch (err) { error.value = user.errorMessage || err.response?.data?.message || err.message || '登录未成功，请稍后重试。' } finally { busy.value = false }
}
onBeforeUnmount(() => { form.password = '' })
</script>

<template>
  <div class="auth-page">
    <section class="auth-story">
      <RouterLink to="/login" class="auth-brand"><span class="brand-symbol"><span></span><span></span><span></span><span></span></span><strong>知行实验室</strong></RouterLink>
      <div class="auth-story-content"><h1>让每一个想法，<br>有实验的空间。</h1><p>从预约一台设备，到完成一次探索。<br>让资源有序共享，让实验安心进行。</p>
        <div class="auth-lab-scene"><LabScene /></div>
      </div><div class="auth-story-footer"><span class="live-dot"></span>共享资源 · 连接灵感 · 安心探索</div>
    </section>
    <section class="auth-form-side">
      <div class="auth-topline">还没有账号？ <RouterLink to="/register">学生注册 <el-icon><ArrowRight /></el-icon></RouterLink></div>
      <div class="auth-card"><span class="section-kicker">欢迎回来</span><h2>登录工作空间</h2><p class="auth-intro">使用你的账号，继续今天的实验计划。</p>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username"><el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" autocomplete="username" maxlength="64" :disabled="busy" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password placeholder="请输入密码" autocomplete="current-password" :disabled="busy" /></el-form-item>
          <el-button class="auth-submit" type="primary" native-type="submit" :loading="busy">登录 <el-icon v-if="!busy"><ArrowRight /></el-icon></el-button>
        </el-form><p class="auth-help">忘记密码？<RouterLink to="/recover">通过QQ邮箱找回</RouterLink></p>
      </div><p class="auth-copyright">知行实验室设备预约与故障报修管理平台</p>
    </section>
  </div>
</template>
