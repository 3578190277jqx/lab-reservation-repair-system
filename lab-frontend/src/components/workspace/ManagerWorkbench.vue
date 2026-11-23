<script setup>
import { computed } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import { rowActions, actionNames, repairStatusNames } from '@/utils/workflow'
import StateEmpty from '@/components/common/StateEmpty.vue'
import StateError from '@/components/common/StateError.vue'
import StateLoading from '@/components/common/StateLoading.vue'
const props = defineProps({
  data: Object,
  errors: Object,
  loading: Boolean,
  repairsLoading: Boolean,
  repairStatus: Number,
  profile: Object,
  date: String,
})
defineEmits(['refresh', 'repair-status', 'action'])
const deviceStates = computed(() => [
  ['可用', props.data.overview?.availableEquipmentCount, 'var(--success)'],
  ['已有预约', props.data.overview?.reservedEquipmentCount, 'var(--info)'],
  ['维修中', props.data.overview?.repairingEquipmentCount, 'var(--warning)'],
  ['已报废', props.data.overview?.scrappedEquipmentCount, 'var(--ink-500)'],
])
const number = (value) => (value == null ? '—' : Number(value).toLocaleString('zh-CN'))
const time = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '未提供')
const actions = (row) => rowActions('repair', row, props.profile)
const actionable = (row) => actions(row).filter((action) => action !== 'records')
</script>
<template>
  <div class="workbench">
    <header class="workbench-heading">
      <div>
        <h1>运维工作台</h1>
        <p>{{ profile?.role === 'admin' ? '全校实验室' : '你负责的实验室' }}的预约审核与维修待办</p>
      </div>
      <div>
        <time>{{ date }}</time
        ><el-button :icon="RefreshRight" :loading="loading" @click="$emit('refresh')"
          >刷新数据</el-button
        >
      </div>
    </header>
    <div class="workbench-summary" aria-label="管理概况">
      <span
        >待审核预约
        <strong>{{
          errors.reservations || loading ? '—' : number(data.reservations?.total)
        }}</strong></span
      ><span
        >{{ repairStatusNames[repairStatus] }}工单
        <strong>{{
          errors.repairs || repairsLoading ? '—' : number(data.repairs?.total)
        }}</strong></span
      ><span
        >设备总量
        <strong>{{
          errors.overview || loading ? '—' : number(data.overview?.equipmentCount)
        }}</strong></span
      ><small>按当前账号管理范围展示</small>
    </div>
    <div class="workbench-queues">
      <section class="workbench-queue" aria-labelledby="reservation-queue-heading">
        <header>
          <div>
            <h2 id="reservation-queue-heading">预约审核</h2>
            <p>最近提交的待审核申请 · 最多 5 条</p>
          </div>
          <RouterLink to="/reservation-review?status=0">全部预约</RouterLink>
        </header>
        <StateLoading v-if="loading" :rows="3" compact />
        <StateError
          v-else-if="errors.reservations"
          :description="errors.reservations"
          compact
          @retry="$emit('refresh')"
        />
        <ol v-else-if="data.reservations?.records.length" class="workbench-records">
          <li v-for="row in data.reservations.records" :key="row.id">
            <div class="record-title">
              <h3>{{ row.equipmentName || `设备 #${row.equipmentId}` }}</h3>
              <span class="record-status">待审核</span>
            </div>
            <p v-if="row.labName" class="record-lab">{{ row.labName }}</p>
            <p class="reservation-period">
              <time>{{ time(row.startTime) }}</time
              ><span>至</span><time>{{ time(row.endTime) }}</time>
            </p>
            <p class="record-description">{{ row.purpose || '未填写用途' }}</p>
            <footer>
              <small>预约 #{{ row.id }} · 申请于 {{ time(row.applyTime) }}</small
              ><el-button
                v-if="rowActions('reservation', row, profile).includes('audit')"
                type="primary"
                plain
                @click="$emit('action', 'audit', row)"
                >审核预约</el-button
              >
            </footer>
          </li>
        </ol>
        <StateEmpty
          v-else
          title="当前没有待审核预约"
          description="学生提交的新申请会显示在这里。"
          compact
        />
      </section>
      <section
        class="workbench-queue"
        aria-labelledby="repair-queue-heading"
        :aria-busy="repairsLoading"
      >
        <header>
          <div>
            <h2 id="repair-queue-heading">维修待办</h2>
            <p>所选状态最近提交的工单 · 最多 5 条</p>
          </div>
          <RouterLink :to="`/repairs?status=${repairStatus}`">全部工单</RouterLink>
        </header>
        <el-radio-group
          class="repair-queue-tabs"
          :model-value="repairStatus"
          aria-label="工单状态"
          @update:model-value="$emit('repair-status', $event)"
          ><el-radio-button
            v-for="(label, index) in repairStatusNames.slice(0, 3)"
            :key="index"
            :value="index"
            >{{ label }}</el-radio-button
          ></el-radio-group
        >
        <StateLoading v-if="repairsLoading" :rows="3" compact />
        <StateError
          v-else-if="errors.repairs"
          :description="errors.repairs"
          compact
          @retry="$emit('repair-status', repairStatus)"
        />
        <ol v-else-if="data.repairs?.records.length" class="workbench-records">
          <li v-for="row in data.repairs.records" :key="row.id">
            <div class="record-title">
              <h3>{{ row.equipmentName || `设备 #${row.equipmentId}` }}</h3>
              <span class="record-status">{{ repairStatusNames[row.status] }}</span>
            </div>
            <p v-if="row.labName" class="record-lab">{{ row.labName }}</p>
            <p class="record-description">{{ row.description }}</p>
            <p class="record-time">报修于 {{ time(row.createTime) }}</p>
            <p v-if="[1, 2].includes(row.status) && !actionable(row).length" class="handler-note">
              由处理人 ID {{ row.handlerId || '未提供' }} 继续处理
            </p>
            <footer>
              <el-button text @click="$emit('action', 'records', row)">工单详情</el-button
              ><el-button
                v-for="action in actionable(row)"
                :key="action"
                type="primary"
                plain
                @click="$emit('action', action, row)"
                >{{ actionNames[action] }}</el-button
              >
            </footer>
          </li>
        </ol>
        <StateEmpty
          v-else
          :title="`当前没有${repairStatusNames[repairStatus]}工单`"
          description="可切换其他状态，或进入工单中心查看全部记录。"
          compact
        />
      </section>
    </div>
    <section class="workbench-health" aria-labelledby="device-health-heading">
      <header>
        <h2 id="device-health-heading">设备状态快照</h2>
        <RouterLink to="/stats">统计分析</RouterLink>
      </header>
      <StateError
        v-if="errors.overview"
        :description="errors.overview"
        compact
        @retry="$emit('refresh')"
      />
      <template v-else
        ><ul>
          <li v-for="[label, value, color] in deviceStates" :key="label">
            <i :style="{ background: color }" />{{ label
            }}<strong>{{ loading ? '—' : number(value) }}</strong>
          </li>
        </ul>
        <p>
          已有预约的设备仍可申请其他空闲时段。此处是当前设备状态，不代表所有时段的可用性。
        </p></template
      >
    </section>
  </div>
</template>
<style scoped>
.workbench {
  display: grid;
  gap: var(--sp-2);
}
.workbench-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-2);
}
.workbench-heading h1 {
  font-size: var(--fs-xl);
  color: var(--ink-900);
  margin: 0;
  line-height: 1.4;
}
.workbench-heading p {
  margin: var(--sp-1) 0 0;
  line-height: 1.6;
}
.workbench-heading > div:last-child {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}
.workbench-heading time {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}
.workbench-summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2) var(--sp-3);
  padding: var(--sp-2) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.workbench-summary span {
  display: inline-flex;
  align-items: baseline;
  gap: var(--sp-1);
}
.workbench-summary strong {
  font-size: var(--fs-lg);
  color: var(--ink-900);
  font-variant-numeric: tabular-nums;
}
.workbench-summary small {
  align-self: center;
  color: var(--ink-500);
  margin-left: auto;
  font-size: var(--fs-xs);
}
.workbench-queues {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: start;
  gap: var(--sp-2);
}
.workbench-queue,
.workbench-health {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  min-width: 0;
}
.workbench-queue > header,
.workbench-health > header {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-2);
  padding: var(--sp-2);
  align-items: baseline;
}
h2 {
  margin: 0;
  font-size: var(--fs-lg);
  color: var(--ink-900);
}
.workbench-queue header p {
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--ink-500);
  margin: 4px 0 0;
}
header a {
  flex-shrink: 0;
  color: var(--brand-600);
  font-size: var(--fs-sm);
}
.workbench-records {
  margin: 0;
  list-style: none;
  padding: 0;
}
.workbench-records li {
  border-top: 1px solid var(--line);
  padding: var(--sp-2);
}
.record-title {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--sp-1);
  align-items: baseline;
}
h3 {
  font-size: var(--fs-md);
  color: var(--ink-900);
  margin: 0;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.record-status {
  font-size: var(--fs-sm);
  color: var(--warning);
}
.workbench-records p {
  margin: var(--sp-1) 0;
  line-height: 1.7;
  font-size: var(--fs-sm);
}
.record-lab,
.record-time {
  color: var(--ink-500);
}
.reservation-period {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--sp-1);
  color: var(--ink-900);
  font-variant-numeric: tabular-nums;
}
.record-description {
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-1);
  margin-top: var(--sp-1);
}
footer small {
  color: var(--ink-500);
  font-size: var(--fs-xs);
}
footer .el-button + .el-button {
  margin-left: 0;
}
.repair-queue-tabs {
  margin: 0 var(--sp-2) var(--sp-2);
}
.handler-note {
  color: var(--ink-500);
}
.workbench-health ul {
  list-style: none;
  padding: 0 var(--sp-2);
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2) var(--sp-4);
}
.workbench-health li {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}
.workbench-health i {
  width: 8px;
  height: 8px;
  border-radius: var(--r-round);
}
.workbench-health strong {
  color: var(--ink-900);
  font-variant-numeric: tabular-nums;
}
.workbench-health > p {
  margin: var(--sp-2);
  color: var(--ink-500);
  font-size: var(--fs-xs);
  line-height: 1.7;
}
@media (max-width: 1199px) {
  .workbench-queues {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 699px) {
  .workbench-heading {
    align-items: flex-start;
  }
  .workbench-heading > div:last-child time {
    display: none;
  }
  .workbench-heading > div:last-child {
    flex-shrink: 0;
  }
  .workbench-summary {
    gap: var(--sp-1) var(--sp-2);
  }
  .workbench-summary small {
    margin-left: 0;
    width: 100%;
  }
  .workbench-health li {
    min-width: 110px;
  }
  header a {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
  }
  .repair-queue-tabs :deep(.el-radio-button__inner) {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
  }
}
</style>
