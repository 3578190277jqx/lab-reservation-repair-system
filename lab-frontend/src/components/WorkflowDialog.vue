<script setup>
import { useWorkflowDialog } from '@/composables/useWorkflowDialog'
import EquipmentPicker from './EquipmentPicker.vue'
import ReservationActionForm from './workflow/ReservationActionForm.vue'
import RepairActionForm from './workflow/RepairActionForm.vue'
import RepairDetails from './workflow/RepairDetails.vue'
import WorkflowSurface from './workflow/WorkflowSurface.vue'
const props = defineProps({ controller: Object, inline: Boolean, hideSchedule: Boolean })
const emit = defineEmits(['success', 'close-request'])
// 外部控制器由设备工作区持有，抽屉与桌面侧栏共用同一份草稿。
const controller =
  props.controller || useWorkflowDialog({ onSuccess: (payload) => emit('success', payload) })
const {
  visible,
  action,
  row,
  busy,
  uploading,
  error,
  form,
  title,
  lockedEquipment,
  isCreate,
  open,
  close,
  submit,
} = controller
function requestClose(done) {
  if (busy.value || uploading.value) return
  // 内嵌预约的草稿由工作区统一管理；普通弹窗仍使用原关闭逻辑。
  if (props.inline && action.value === 'apply') emit('close-request')
  else close(done)
}
defineExpose({ open })
</script>

<template>
  <WorkflowSurface
    :inline="inline"
    :visible="visible"
    :title="title"
    :locked="busy || uploading"
    :close="requestClose"
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="workflow-alert"
    />
    <el-form
      v-if="action !== 'records'"
      label-position="top"
      :disabled="busy || uploading"
      @submit.prevent="submit"
    >
      <template v-if="isCreate">
        <el-form-item label="设备" required>
          <el-input
            v-if="lockedEquipment"
            :model-value="`${row.name} · ${row.equipmentCode || row.id}`"
            disabled
          />
          <EquipmentPicker
            v-else
            v-model="form.equipmentId"
            :mode="action"
            :disabled="busy || uploading"
          />
        </el-form-item>
      </template>
      <ReservationActionForm
        v-if="['apply', 'audit', 'cancel', 'signIn'].includes(action)"
        :controller="controller"
        :inline="inline"
        :hide-schedule="hideSchedule"
      />
      <RepairActionForm
        v-if="['submit', 'accept', 'start', 'complete'].includes(action)"
        :controller="controller"
      />
    </el-form>
    <RepairDetails v-else :controller="controller" />
    <template #footer
      ><el-button :disabled="busy || uploading" @click="requestClose()">{{
        action === 'records' ? '关闭' : '取消'
      }}</el-button
      ><el-button
        v-if="action !== 'records'"
        type="primary"
        :loading="busy"
        :disabled="uploading"
        @click="submit"
        >{{ action === 'apply' || action === 'submit' ? '提交' : '确认' }}</el-button
      ></template
    >
  </WorkflowSurface>
</template>

<style scoped>
.workflow-alert {
  margin-bottom: 18px;
}
</style>
<style>
.workflow-dialog {
  max-width: calc(100vw - 28px);
}
@media (max-width: 600px) {
  .workflow-dialog .el-date-editor--datetimerange {
    flex-wrap: wrap;
    height: auto;
    min-height: 50px;
  }
}
</style>
