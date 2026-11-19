<script setup>
defineProps({
  label: { type: String, default: '正在加载…' },
  rows: { type: Number, default: 3, validator: value => Number.isInteger(value) && value >= 0 && value <= 12 },
  compact: Boolean,
})
</script>

<template>
  <div class="state-feedback state-loading" :class="{ 'is-compact': compact }" role="status" aria-live="polite" aria-busy="true">
    <span class="state-loading-label">{{ label }}</span>
    <div class="state-loading-content" aria-hidden="true"><slot><el-skeleton :rows="rows" /></slot></div>
  </div>
</template>
