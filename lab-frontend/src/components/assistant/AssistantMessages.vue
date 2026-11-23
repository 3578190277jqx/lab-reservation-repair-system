<script setup>
import { assistantModes } from '@/utils/assistant-conversation'
import AssistantPending from './AssistantPending.vue'
defineProps({ messages: { type: Array, required: true }, canRetry: Boolean, copiedId: Number })
defineEmits(['go', 'retry', 'copy'])
</script>
<template>
  <div class="conversation" aria-live="polite" aria-relevant="additions text">
    <div v-if="!messages.length" class="conversation-empty">可以先描述设备的故障，再追问“帮我缩短成一句话”。</div>
    <article v-for="(item, index) in messages" :key="item.id" class="answer-card" :aria-busy="!!item.loading">
      <strong class="answer-question">{{ item.question }}</strong>
      <p class="answer-mode">{{ item.loading ? '正在回答…' : assistantModes[item.mode] || '参考回复' }}</p>
      <AssistantPending v-if="item.loading" />
      <p v-if="item.answer" class="answer-text" :class="{ 'answer-error': item.mode === 'error' }">{{ item.answer }}</p>
      <div class="answer-actions">
        <el-button v-if="item.answer && item.mode !== 'error' && !item.loading" @click="$emit('copy', item.id)">{{ copiedId === item.id ? '已复制' : '复制回答' }}</el-button>
        <el-button v-if="index === messages.length - 1 && item.retryable" :disabled="!canRetry" type="primary" plain @click="$emit('retry')">重试这条问题</el-button>
        <el-button v-for="action in item.actions" :key="action.code" @click="$emit('go', action.code)">{{ action.label }} →</el-button>
      </div>
    </article>
  </div>
</template>
<style scoped>
.conversation-empty{padding:24px 12px;text-align:center;color:var(--el-text-color-secondary);line-height:1.8}
.answer-card{background:var(--el-fill-color-light);border-radius:12px;padding:18px;margin:16px 0}
.answer-question,.answer-text{overflow-wrap:anywhere;white-space:pre-wrap;line-height:1.8}
.answer-mode{font-size:12px;color:var(--el-text-color-secondary)}.answer-error{color:var(--el-color-danger)}
.answer-actions{display:flex;flex-wrap:wrap;gap:10px}.answer-actions .el-button{margin:0;white-space:normal;height:auto;min-height:34px;text-align:left}
</style>
