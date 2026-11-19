<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, RefreshRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { safeRedirect } from '@/utils/navigation'
const user = useUserStore()
const route = useRoute()
const router = useRouter()
const busy = ref(false)
const error = ref('')
async function retry() { if (busy.value) return; busy.value = true; error.value = ''; try { await user.fetchUserInfo(); await router.replace(safeRedirect(route.query.redirect) || '/home') } catch (err) { error.value = user.errorMessage || err.message || '暂时无法连接服务，请稍后重试。' } finally { busy.value = false } }
function backToLogin() { user.resetSession(); router.replace('/login') }
</script>
<template><main class="standalone-state"><div class="state-card"><span class="state-icon"><el-icon><Connection /></el-icon></span><span class="section-kicker">连接暂时中断</span><h1>暂时无法确认登录状态</h1><p>登录凭证已保留。请检查网络连接，或稍后重新尝试。</p><el-alert v-if="error || user.errorMessage" :title="error || user.errorMessage" type="error" :closable="false" class="form-alert" /><div class="state-actions"><el-button type="primary" :icon="RefreshRight" :loading="busy" @click="retry">重新连接</el-button><el-button :disabled="busy" @click="backToLogin">重新登录</el-button></div></div></main></template>
