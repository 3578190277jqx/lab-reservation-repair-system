<script setup>
import { ElIcon } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
</script>

<template>
  <div class="pending-feedback" data-assistant-pending aria-hidden="true">
    <ElIcon class="pending-feedback-icon is-loading" :size="20"><Loading /></ElIcon>
    <span class="pending-feedback-label">请稍候</span>
  </div>
</template>

<style scoped>
.pending-feedback {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  margin: 8px 0 12px;
  color: var(--el-color-primary);
}
.pending-feedback-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}
@media (prefers-reduced-motion: reduce) {
  .pending-feedback-icon.is-loading {
    animation: none;
  }
}
</style>
