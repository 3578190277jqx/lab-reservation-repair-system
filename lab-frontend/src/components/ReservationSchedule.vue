<script setup>
import { computed } from 'vue'
import { useReservationSchedule } from '@/composables/useReservationSchedule'
import { beijingTime } from '@/utils/workflow'
const props = defineProps({
  equipmentId: [String, Number],
  startTime: String,
  endTime: String,
  refreshKey: Number,
  controller: Object,
})
const { date, schedule, loading, error, conflict, load } =
  props.controller || useReservationSchedule(props)
// 排期接口不暴露预约 ID；相同匿名时段以出现次数区分，避免重复 key。
const keyedSlots = computed(() => {
  const occurrences = new Map()
  return (schedule.value?.slots || []).map((slot) => {
    const identity = JSON.stringify([slot.startTime, slot.endTime, slot.status])
    const occurrence = occurrences.get(identity) || 0
    occurrences.set(identity, occurrence + 1)
    return { slot, key: JSON.stringify([identity, occurrence]) }
  })
})
const bars = computed(() => {
  const day = beijingTime(`${date.value} 00:00:00`)
  return (schedule.value?.slots || []).map((slot) => {
    const start = Math.max(day, beijingTime(slot.startTime)),
      end = Math.min(day + 86400000, beijingTime(slot.endTime))
    return {
      left: `${Math.max(0, (start - day) / 864000)}%`,
      width: `${Math.max(0, (end - start) / 864000)}%`,
    }
  })
})
</script>
<template>
  <section class="schedule-panel" aria-label="设备占用时段" :aria-busy="loading">
    <div class="schedule-toolbar">
      <strong>设备占用时段</strong
      ><el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        :clearable="false"
        aria-label="查看占用日期"
      /><el-button :loading="loading" :disabled="!equipmentId" @click="load">刷新</el-button>
    </div>
    <p class="schedule-hint">
      北京时间。待审核也占用时段；支持相邻预约，结束时间等于下一条开始时间时不冲突。
    </p>
    <p v-if="!equipmentId">请先选择设备。</p>
    <p v-else-if="loading" role="status">正在查询占用时段…</p>
    <div v-else-if="error" role="alert">
      <el-alert :title="error" type="warning" :closable="false" /><el-button
        class="schedule-retry"
        @click="load"
        >重试排期</el-button
      >
    </div>
    <template v-else-if="schedule">
      <el-alert
        v-if="!schedule.bookable"
        :title="schedule.reason"
        type="warning"
        :closable="false"
      />
      <el-alert
        v-if="conflict"
        title="所选时间与已显示的预约重叠，请调整时间或刷新排期。"
        type="warning"
        :closable="false"
      />
      <div v-if="schedule.slots.length" class="schedule-scale" aria-hidden="true">
        <span>00:00</span><span>12:00</span><span>24:00</span>
      </div>
      <ul v-if="schedule.slots.length" class="schedule-slots">
        <li v-for="({ slot, key }, index) in keyedSlots" :key="key">
          <div class="slot-track" aria-hidden="true">
            <span :class="{ pending: slot.status === 0 }" :style="bars[index]" />
          </div>
          <span class="slot-time">{{ slot.startTime }} 至 {{ slot.endTime }}</span
          ><el-tag :type="slot.status === 0 ? 'warning' : 'success'">{{
            slot.status === 0 ? '待审核' : '已通过'
          }}</el-tag>
        </li>
      </ul>
      <p v-else>该日期暂无待审核或已通过预约。</p>
      <p v-if="schedule.truncated" class="schedule-warning">
        记录较多，仅显示前200条，未显示时段不代表空闲。
      </p>
    </template>
    <p class="schedule-hint">
      这里只展示所选日期的占用快照。跨日申请请分别查看各日期，最终以提交时的检查为准。
    </p>
  </section>
</template>
<style scoped>
.schedule-panel {
  padding: var(--sp-2);
  margin-bottom: var(--sp-2);
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--canvas);
}
.schedule-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
  align-items: center;
}
.schedule-toolbar strong {
  width: 100%;
  color: var(--ink-900);
  font-size: var(--fs-md);
}
.schedule-toolbar :deep(.el-date-editor) {
  width: 170px;
  max-width: 100%;
}
.schedule-hint {
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--ink-500);
  margin: var(--sp-1) 0;
}
.schedule-slots {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 272px;
  overflow: auto;
}
.schedule-slots li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-1);
  padding: var(--sp-1) 0;
  border-bottom: 1px solid var(--line);
  font-size: var(--fs-sm);
}
.slot-time {
  flex: 1 1 140px;
  font-variant-numeric: tabular-nums;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.schedule-scale {
  display: flex;
  justify-content: space-between;
  margin-top: var(--sp-2);
  color: var(--ink-500);
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
}
.slot-track {
  position: relative;
  height: 8px;
  width: 100%;
  background: var(--line);
  border-radius: var(--r-sm);
  overflow: hidden;
}
.slot-track span {
  position: absolute;
  height: 100%;
  background: var(--brand-600);
}
.slot-track .pending {
  background: var(--warning);
}
.schedule-warning {
  color: var(--warning);
  font-size: var(--fs-sm);
  line-height: 1.7;
}
.schedule-retry {
  margin-top: var(--sp-1);
}
</style>
