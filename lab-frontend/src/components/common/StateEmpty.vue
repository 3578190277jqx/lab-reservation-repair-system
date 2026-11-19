<script setup>
import { Box } from '@element-plus/icons-vue'

defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
  compact: Boolean,
})
const emit = defineEmits(['action'])
</script>

<template>
  <div class="state-feedback state-empty" :class="{ 'is-compact': compact }" role="status">
    <el-icon class="state-feedback-icon" aria-hidden="true"><Box /></el-icon>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot><el-button v-if="actionLabel" type="primary" plain @click="emit('action')">{{ actionLabel }}</el-button></slot>
  </div>
</template>
