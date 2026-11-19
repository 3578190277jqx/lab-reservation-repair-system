<script setup>
import { Warning } from '@element-plus/icons-vue'

defineProps({
  title: { type: String, default: '加载失败' },
  description: { type: String, default: '请稍后重新加载。' },
  retryLabel: { type: String, default: '重新加载' },
  disabled: Boolean,
  compact: Boolean,
})
const emit = defineEmits(['retry'])
</script>

<template>
  <div class="state-feedback state-error" :class="{ 'is-compact': compact }" role="alert">
    <el-icon class="state-feedback-icon" aria-hidden="true"><Warning /></el-icon>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <el-button :disabled="disabled" @click="emit('retry')">{{ retryLabel }}</el-button>
  </div>
</template>
