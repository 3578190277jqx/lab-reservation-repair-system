<script setup>
import { computed } from 'vue'
import { rowActions } from '@/utils/workflow'
import WorkflowDialog from '@/components/WorkflowDialog.vue'
import ReservationSchedule from '@/components/ReservationSchedule.vue'
const props = defineProps({ equipment: Object, profile: Object, booking: Object, schedule: Object })
defineEmits(['report', 'cancel-booking'])
const actions = computed(() => rowActions('equipment', props.equipment, props.profile))
const blocked = computed(() => props.schedule.schedule.value?.bookable === false)
</script>
<template>
  <div class="planning-content">
    <header>
      <p class="planning-context">所选设备</p>
      <h2>{{ equipment.name }}</h2>
      <p>{{ equipment.labName || `实验室 #${equipment.labId}` }}</p>
    </header>
    <details class="device-details">
      <summary>设备资料</summary>
      <dl>
        <dt>型号</dt>
        <dd>{{ equipment.model || '未登记' }}</dd>
        <dt>编号</dt>
        <dd>{{ equipment.equipmentCode || equipment.id }}</dd>
        <dt>说明</dt>
        <dd>{{ equipment.description || '未登记' }}</dd>
        <dt>位置</dt>
        <dd>{{ equipment.labLocation || '未登记' }}</dd>
        <dt>厂商</dt>
        <dd>{{ equipment.manufacturer || '未登记' }}</dd>
        <dt>购置</dt>
        <dd>{{ equipment.purchaseDate || '未登记' }}</dd>
      </dl>
    </details>
    <ReservationSchedule :equipment-id="equipment.id" :controller="schedule" />
    <template v-if="!booking.visible.value">
      <el-button
        v-if="actions.includes('apply')"
        type="primary"
        class="booking-entry"
        :disabled="blocked"
        @click="booking.open('apply', equipment)"
        >申请这台设备</el-button
      >
      <p class="planning-rule">
        {{
          actions.includes('apply')
            ? '已有预约也可申请其他时段。排期仅作参考，提交时再次检查时间冲突及预约资格。'
            : equipment.status === 2
              ? '设备维修中，暂不能申请预约。'
              : '该设备当前不支持预约。'
        }}
      </p>
    </template>
    <WorkflowDialog
      :controller="booking"
      inline
      hide-schedule
      @close-request="$emit('cancel-booking')"
    />
    <el-button
      v-if="actions.includes('submit')"
      text
      class="report-entry"
      :disabled="booking.busy.value"
      @click="$emit('report', equipment)"
      >发现故障？提交报修</el-button
    >
  </div>
</template>
<style scoped>
.planning-content {
  min-width: 0;
}
header h2 {
  margin: 4px 0;
  font-size: var(--fs-lg);
  color: var(--ink-900);
  line-height: 1.5;
  overflow-wrap: anywhere;
}
header p {
  margin: 0 0 var(--sp-1);
  line-height: 1.6;
}
.planning-context {
  color: var(--ink-500);
  font-size: var(--fs-xs);
}
.device-details {
  margin-bottom: var(--sp-2);
  padding: var(--sp-1) 0;
  border-bottom: 1px solid var(--line);
}
summary {
  cursor: pointer;
  color: var(--brand-600);
  line-height: 2;
}
dl {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: var(--sp-1);
  line-height: 1.7;
}
dt {
  color: var(--ink-500);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.booking-entry {
  width: 100%;
}
.planning-rule {
  font-size: var(--fs-sm);
  color: var(--ink-500);
  line-height: 1.7;
}
.report-entry {
  margin: var(--sp-1) 0 0;
  white-space: normal;
  min-height: 44px;
}
</style>
