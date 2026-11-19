<script setup>
import { shallowRef } from 'vue'
import { useEquipmentExcel } from '@/composables/useEquipmentExcel'
const props = defineProps({
  resource: { type: String, required: true },
  role: { type: String, required: true },
  getFilters: { type: Function, default: () => ({}) },
})
const emit = defineEmits(['imported'])
const fileInput = shallowRef(null)
const { visible, busy, error, success, exportRows, downloadTemplate, uploadFile } =
  useEquipmentExcel({
    resource: () => props.resource,
    role: () => props.role,
    getFilters: () => props.getFilters(),
    onImported: (result) => emit('imported', result),
  })
async function selected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) await uploadFile(file)
}
</script>
<template>
  <div v-if="visible" class="excel-actions">
    <div class="excel-buttons">
      <el-button :disabled="!!busy" :loading="busy === 'export'" @click="exportRows"
        >导出当前筛选</el-button
      >
      <el-button :disabled="!!busy" :loading="busy === 'template'" @click="downloadTemplate"
        >导入模板</el-button
      >
      <el-button :disabled="!!busy" :loading="busy === 'import'" @click="fileInput.click()"
        >导入设备</el-button
      >
      <input
        ref="fileInput"
        class="file-input"
        type="file"
        accept=".xlsx"
        aria-label="选择设备导入文件"
        @change="selected"
      />
    </div>
    <span class="excel-hint">仅新增设备，最多 500 行 / 2 MiB；状态由系统维护。</span>
    <span v-if="error" class="excel-error" role="alert">{{ error }}</span>
    <span v-if="success" class="excel-success" role="status">{{ success }}</span>
  </div>
</template>
<style scoped>
.excel-actions {
  display: grid;
  gap: var(--sp-1, 8px);
}
.excel-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1, 8px);
}
.excel-buttons .el-button + .el-button {
  margin-left: 0;
}
.file-input {
  display: none;
}
.excel-hint {
  font-size: var(--fs-xs, 12px);
  color: var(--ink-500);
}
.excel-error {
  color: var(--el-color-danger);
}
.excel-success {
  color: var(--brand-700);
}
</style>
