<script setup>
import ReservationSchedule from '../ReservationSchedule.vue'

const props = defineProps({
  controller: { type: Object, required: true },
  inline: Boolean,
  hideSchedule: Boolean,
})
const {
  action,
  row,
  busy,
  uploading,
  visible,
  form,
  scheduleRevision,
  defaultReservationTimes,
  timeIssue,
  reservationTimeChanged,
  chooseTomorrow,
  disablePastDate,
} = props.controller
function setTime(index, value) {
  const times = [...(form.times || [])]
  times[index] = value
  form.times = times
  reservationTimeChanged()
}
</script>

<template>
  <template v-if="action === 'apply'">
    <template v-if="inline">
      <el-form-item label="开始时间（北京时间）" required>
        <el-date-picker
          :model-value="form.times?.[0]"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="选择开始日期和时间"
          :disabled-date="disablePastDate"
          aria-label="预约开始时间（北京时间）"
          :aria-invalid="!!timeIssue"
          aria-describedby="inline-time-error"
          style="width: 100%"
          @update:model-value="setTime(0, $event)"
        />
      </el-form-item>
      <el-form-item label="结束时间（北京时间）" required>
        <el-date-picker
          :model-value="form.times?.[1]"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="选择结束日期和时间"
          :disabled-date="disablePastDate"
          aria-label="预约结束时间（北京时间）"
          :aria-invalid="!!timeIssue"
          aria-describedby="inline-time-error"
          style="width: 100%"
          @update:model-value="setTime(1, $event)"
        />
      </el-form-item>
    </template>
    <el-form-item v-else label="预约时间（北京时间）" required>
      <el-date-picker
        v-model="form.times"
        type="datetimerange"
        popper-class="lab-range-picker"
        value-format="YYYY-MM-DD HH:mm:ss"
        format="YYYY-MM-DD HH:mm"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        range-separator="至"
        :disabled-date="disablePastDate"
        :default-time="defaultReservationTimes"
        style="width: 100%"
        @change="reservationTimeChanged"
      />
    </el-form-item>
    <p
      v-if="timeIssue"
      :id="inline ? 'inline-time-error' : undefined"
      class="reservation-time-error"
      role="alert"
    >
      {{ timeIssue }}
    </p>
    <div class="reservation-time-shortcut">
      <el-button size="small" :disabled="busy || uploading" @click="chooseTomorrow"
        >选择明天 09:00–10:00</el-button
      ><span>仅填入时间，仍需确认提交</span>
    </div>
    <p class="workflow-hint">
      请选择未来时段。已有预约的设备可申请其他空闲时段，提交时系统检查时间冲突。
    </p>
    <ReservationSchedule
      v-if="visible && !hideSchedule"
      :equipment-id="form.equipmentId"
      :start-time="form.times?.[0]"
      :end-time="form.times?.[1]"
      :refresh-key="scheduleRevision"
    />
    <el-form-item label="预约用途" required
      ><el-input
        aria-label="预约用途"
        v-model="form.purpose"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="请说明实验课程或使用目的"
    /></el-form-item>
  </template>
  <template v-if="action === 'audit'">
    <p>
      预约 #{{ row.id }} · 设备 {{ row.equipmentId }}<br />{{ row.startTime }} 至 {{ row.endTime
      }}<br />用途：{{ row.purpose }}
    </p>
    <el-form-item label="审核结果" required
      ><el-radio-group v-model="form.approved"
        ><el-radio :value="true">通过</el-radio
        ><el-radio :value="false">拒绝</el-radio></el-radio-group
      ></el-form-item
    >
    <el-form-item label="审核意见" :required="!form.approved"
      ><el-input
        v-model="form.remark"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="拒绝时请说明原因"
    /></el-form-item>
  </template>
  <template v-if="action === 'cancel'">
    <p>确认取消预约 #{{ row.id }}？</p>
    <el-form-item label="取消原因（选填）"
      ><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
    /></el-form-item>
  </template>
  <template v-if="action === 'signIn'"
    ><p>确认对预约 #{{ row.id }} 签到？签到时间由系统记录。</p>
    <p class="workflow-hint">须在预约开始后30分钟内，且预约结束前签到。</p></template
  >
</template>

<style scoped>
.workflow-hint {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}
.reservation-time-error {
  color: #c45656;
  font-size: 13px;
  line-height: 1.7;
  margin: -6px 0 12px;
}
.reservation-time-shortcut {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-bottom: 12px;
}
.reservation-time-shortcut .el-button {
  min-height: 36px;
}
.reservation-time-shortcut span {
  color: #64748b;
  font-size: 12px;
}
</style>
