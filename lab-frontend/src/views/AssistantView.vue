<script setup>
import { computed, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { copyText } from '@/utils/clipboard'
import { useUserStore } from '@/stores/user'
import { assistantShortcuts } from '@/utils/assistant-actions'
import { useAssistantConversation } from '@/composables/useAssistantConversation'
import AssistantMessages from '@/components/assistant/AssistantMessages.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'

const user = useUserStore(), router = useRouter()
const { question, messages, busy, error, enabled, pending, canRetry, ask, retry, clear, go, cancelNavigation } = useAssistantConversation({
  session: () => ({ token: user.token === getToken() ? user.token : '', role: user.role }),
  request, navigate: destination => router.push(destination),
})
const shortcuts = computed(() => assistantShortcuts(user.role))
const suggestions = ['我要预约设备', '怎么预约和签到？', '设备已有预约，还能申请吗？', '帮我整理报修描述：设备开机后无显示，今天首次出现。']
const copiedId = shallowRef(null)
watch(messages, () => { if (!messages.value.some(item => item.id === copiedId.value)) copiedId.value = null })
async function copy(id) {
  const item = messages.value.find(message => message.id === id)
  if (!item?.answer || !getToken()) return
  try { await copyText(item.answer); if (messages.value.some(message => message.id === id)) copiedId.value = id }
  catch (e) { ElMessage.warning(e.message) }
}
</script>
<template>
  <div class="page-stack assistant-page">
    <div class="page-heading"><div><h1>实验室操作助手</h1><p class="page-description">连续咨询使用规则、整理报修描述，也可以说“我要预约设备”前往办理。</p></div></div>
    <section class="assistant-shortcuts" aria-label="快捷操作">
      <button v-for="action in shortcuts" :key="action.code" class="shortcut-card" @click="go(action.code)"><strong>{{ action.label }} <span aria-hidden="true">→</span></strong><span>{{ action.hint }}</span></button>
    </section>
    <section class="surface-card assistant-box">
      <el-alert :title="enabled === true ? '问题和最近3轮问答将发送到云端模型，请勿输入密码、验证码或个人敏感信息。' : enabled === false ? '文字模型尚未启用，操作指南和快捷跳转仍可使用。' : '正在确认模型状态，快捷操作可直接使用。'" type="info" :closable="false" />
      <div class="quick-questions"><el-button v-for="item in suggestions" :key="item" :disabled="busy" @click="ask(item)">{{ item }}</el-button></div>
      <AssistantMessages :messages="messages" :can-retry="canRetry" :copied-id="copiedId" @go="go" @retry="retry" @copy="copy" />
      <div v-if="pending" class="navigation-notice" role="status"><strong>{{ pending.seconds }} 秒后打开：{{ pending.label }}</strong><p>{{ pending.hint }}</p><div class="navigation-actions"><el-button type="primary" @click="go(pending.code)">立即前往</el-button><el-button @click="cancelNavigation">留在这里</el-button></div></div>
      <AssistantComposer v-model="question" :busy="busy" :error="error" @send="ask()" @clear="clear" @edit="cancelNavigation" />
      <p class="page-description">导航请求会在3秒后打开页面，可以选择“留在这里”。预约与工单仍由你确认提交。</p>
    </section>
  </div>
</template>
<style scoped>
.assistant-page{max-width:1040px;margin:auto}.assistant-box{padding:24px}
.assistant-shortcuts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
.shortcut-card{text-align:left;padding:18px;border:1px solid var(--el-border-color-light);border-radius:14px;background:var(--el-bg-color);color:var(--el-text-color-primary);cursor:pointer}
.shortcut-card:hover{border-color:var(--el-color-primary);background:var(--el-fill-color-light)}.shortcut-card:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:2px}
.shortcut-card strong{display:flex;justify-content:space-between;font-size:15px}.shortcut-card>span{display:block;margin-top:8px;font-size:13px;color:var(--el-text-color-secondary);line-height:1.6}
.quick-questions,.navigation-actions{display:flex;flex-wrap:wrap;gap:10px}.quick-questions{margin:20px 0}
.quick-questions .el-button,.navigation-actions .el-button{margin:0;white-space:normal;height:auto;min-height:34px;text-align:left}
.navigation-notice{padding:16px;margin:16px 0;border:1px solid var(--el-color-primary-light-5);border-radius:12px;background:var(--el-color-primary-light-9)}.navigation-notice p{font-size:13px;line-height:1.6}
@media(max-width:600px){.assistant-box{padding:16px}.assistant-shortcuts{grid-template-columns:1fr}.shortcut-card{padding:14px}}
</style>
