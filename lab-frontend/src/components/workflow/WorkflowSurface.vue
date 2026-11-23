<script setup>
defineProps({ inline: Boolean, visible: Boolean, title: String, locked: Boolean, close: Function })
</script>
<template>
  <section v-if="inline && visible" class="inline-workflow" aria-label="申请预约">
    <h3>{{ title }}</h3>
    <slot />
    <footer><slot name="footer" /></footer>
  </section>
  <el-dialog
    v-else-if="!inline"
    :model-value="visible"
    :title="title"
    width="620px"
    class="workflow-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!locked"
    :show-close="!locked"
    :before-close="close"
    destroy-on-close
  >
    <slot />
    <template #footer><slot name="footer" /></template>
  </el-dialog>
</template>
<style scoped>
.inline-workflow {
  border-top: 1px solid var(--line);
  margin-top: var(--sp-2);
  padding-top: var(--sp-2);
}
h3 {
  font-size: var(--fs-md);
  margin: 0 0 var(--sp-2);
  color: var(--ink-900);
}
footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-1);
  padding-top: var(--sp-1);
}
footer :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
