<script setup>
import { computed, ref } from 'vue'
import { RefreshRight, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import BindContactPanel from '@/components/BindContactPanel.vue'

const user = useUserStore()
const busy = ref(false)
const error = ref('')
const profile = computed(() => user.user || {})
const roleName = computed(() => ({ admin: '系统管理员', lab_admin: '实验室管理员', student: '学生' }[profile.value.role] || '未提供'))
const fields = computed(() => [
  ['用户名', profile.value.username], ['真实姓名', profile.value.realName],
  ['学号', profile.value.studentNo], ['联系电话', profile.value.phone],
  ['邮箱地址', profile.value.email], ['用户编号', profile.value.id],
  ['最近登录', profile.value.lastLoginTime], ['账号创建时间', profile.value.createTime],
])
const textValue = value => value === null || value === undefined || value === '' ? '未提供'
  : String(value).replace(/^(\d{4}-\d{2}-\d{2})T(?=\d{2}:\d{2})/, '$1 ')
async function refresh() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await user.fetchUserInfo({ force: true }) } catch (err) { error.value = user.errorMessage || err.message || '个人信息加载失败，请重试。' } finally { busy.value = false }
}
</script>

<template>
  <div class="page-stack">
    <div class="page-heading"><div><span class="section-kicker">ACCOUNT</span><h1>个人信息</h1><p class="page-description">查看你的身份信息与联系方式。</p></div><el-button :icon="RefreshRight" :loading="busy" @click="refresh">刷新资料</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <section v-loading="busy" class="surface-card profile-card">
      <div class="profile-summary"><span class="profile-avatar">{{ (user.displayName || '用').slice(0, 1) }}</span><div><h2>{{ user.displayName }}</h2><div class="profile-tags"><el-tag effect="plain">{{ roleName }}</el-tag><el-tag :type="profile.status === 1 ? 'success' : profile.status === 0 ? 'danger' : 'info'" effect="light">{{ profile.status === 1 ? '账号正常' : profile.status === 0 ? '账号已禁用' : '状态未提供' }}</el-tag></div></div></div>
      <dl class="profile-fields"><div v-for="[label, value] in fields" :key="label"><dt>{{ label }}</dt><dd>{{ textValue(value) }}</dd></div></dl>
    </section>
    <BindContactPanel/>
    <div class="profile-hint"><el-icon><User /></el-icon><p>资料需要更正时，请联系平台管理员。</p></div>
  </div>
</template>
