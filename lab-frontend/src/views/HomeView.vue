<script setup>
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { ArrowRight, Calendar, Tools, RefreshRight, OfficeBuilding } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useWorkspace } from '@/composables/useWorkspace'
import { rowActions, actionNames, repairStatusNames } from '@/utils/workflow'
import { reservationStatusName } from '@/utils/reservation-detail'
import WorkflowDialog from '@/components/WorkflowDialog.vue'
import ManagerWorkbench from '@/components/workspace/ManagerWorkbench.vue'
import EquipmentPreview from '@/components/interaction/EquipmentPreview.vue'
import EquipmentSkeleton from '@/components/interaction/EquipmentSkeleton.vue'
import StudentHero from '@/components/interaction/StudentHero.vue'
import EquipmentShowcase from '@/components/interaction/EquipmentShowcase.vue'
import ReservationGuide from '@/components/interaction/ReservationGuide.vue'
import StateEmpty from '@/components/common/StateEmpty.vue'
import StateError from '@/components/common/StateError.vue'
import StateLoading from '@/components/common/StateLoading.vue'
const user = useUserStore(),
  { data, errors, loading, refresh, repairStatus, repairsLoading, changeRepairStatus } =
    useWorkspace()
const workflow = ref(null),
  now = ref(Date.now())
const previewOpen = shallowRef(false)
const selectedEquipment = shallowRef(null)
const reservationGuide = shallowRef(null)
function inspectEquipment(row) {
  selectedEquipment.value = row
  previewOpen.value = true
}
function previewAction({ action, row }) {
  workflow.value?.open(action, row)
}
const clock = setInterval(() => {
  now.value = Date.now()
}, 15000)
onBeforeUnmount(() => clearInterval(clock))
const date = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(now.value),
)
const actions = (kind, row) => rowActions(kind, row, user.user, now.value)
function time(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}
function progress(status) {
  return Math.max(0, Math.min(3, Number(status)))
}
</script>
<template>
  <div
    class="role-home"
    :class="user.isManager ? 'manager-home' : 'learner-home'"
    :aria-busy="loading"
  >
    <StateError
      v-if="errors.page"
      :description="errors.page"
      :disabled="loading"
      compact
      @retry="refresh"
    />
    <ManagerWorkbench
      v-if="user.isManager"
      :data="data"
      :errors="errors"
      :loading="loading"
      :repairs-loading="repairsLoading"
      :repair-status="repairStatus"
      :profile="user.user"
      :date="date"
      @refresh="refresh"
      @repair-status="changeRepairStatus"
      @action="(action, row) => workflow?.open(action, row)"
    />
    <template v-else>
      <StudentHero
        :display-name="user.displayName"
        :date="date"
        @guide="reservationGuide?.open()"
      />
      <div class="student-quick-links">
        <RouterLink to="/reservations"
          ><span class="quick-symbol"
            ><el-icon><Calendar /></el-icon
          ></span>
          <div><strong>我的预约</strong><small>查看审核 · 按时签到</small></div>
          <el-icon><ArrowRight /></el-icon></RouterLink
        ><RouterLink to="/repairs"
          ><span class="quick-symbol amber"
            ><el-icon><Tools /></el-icon
          ></span>
          <div><strong>故障报修</strong><small>提交问题 · 跟进维修</small></div>
          <el-icon><ArrowRight /></el-icon></RouterLink
        ><RouterLink to="/labs"
          ><span class="quick-symbol blue"
            ><el-icon><OfficeBuilding /></el-icon
          ></span>
          <div><strong>实验室指南</strong><small>查看位置 · 联系管理员</small></div>
          <el-icon><ArrowRight /></el-icon
        ></RouterLink>
      </div>
      <div class="student-section-heading">
        <div>
          <h2>为下一次实验找设备</h2>
          <p>以下设备当前状态为可用，具体时段以预约检查为准。</p>
        </div>
        <RouterLink to="/equipment"
          >浏览全部设备 <el-icon><ArrowRight /></el-icon
        ></RouterLink>
      </div>
      <EquipmentSkeleton v-if="loading" /><StateError
        v-else-if="errors.equipment"
        :description="errors.equipment"
        :disabled="loading"
        @retry="refresh"
      />
      <EquipmentShowcase
        v-else-if="data.equipment?.records.length"
        :records="data.equipment.records"
        @inspect="inspectEquipment"
        @apply="workflow?.open('apply', $event)"
      />
      <StateEmpty v-else title="暂时没有状态为可用的设备"
        ><RouterLink to="/equipment">查看其他设备与可预约时段 →</RouterLink></StateEmpty
      >
      <div class="student-personal-grid">
        <section class="work-panel">
          <header>
            <div>
              <h2>最近预约</h2>
              <p>最近提交的3条记录</p>
            </div>
            <RouterLink to="/reservations">查看全部</RouterLink>
          </header>
          <StateLoading v-if="loading" :rows="2" compact /><StateError
            v-else-if="errors.reservations"
            :description="errors.reservations"
            :disabled="loading"
            compact
            @retry="refresh"
          />
          <div v-else-if="data.reservations?.records.length">
            <article v-for="row in data.reservations.records" :key="row.id" class="personal-row">
              <div>
                <strong>{{ row.equipmentName || '设备 #' + row.equipmentId }}</strong
                ><el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{
                  reservationStatusName(row.status)
                }}</el-tag>
              </div>
              <p>{{ time(row.startTime) }} 至 {{ time(row.endTime) }}</p>
              <small>{{ row.purpose }}</small>
              <div class="personal-actions">
                <el-button
                  v-for="action in actions('reservation', row)"
                  :key="action"
                  size="small"
                  :type="action === 'cancel' ? 'default' : 'primary'"
                  @click="workflow.open(action, row)"
                  >{{ actionNames[action] }}</el-button
                >
              </div>
            </article>
          </div>
          <StateEmpty v-else title="还没有预约记录" compact
            ><RouterLink to="/equipment">去选择第一台实验设备 →</RouterLink></StateEmpty
          >
        </section>
        <section class="work-panel">
          <header>
            <div>
              <h2>报修进度</h2>
              <p>最近提交的3条工单</p>
            </div>
            <RouterLink to="/repairs">查看全部</RouterLink>
          </header>
          <StateLoading v-if="loading" :rows="2" compact /><StateError
            v-else-if="errors.repairs"
            :description="errors.repairs"
            :disabled="loading"
            compact
            @retry="refresh"
          />
          <div v-else-if="data.repairs?.records.length">
            <article v-for="row in data.repairs.records" :key="row.id" class="personal-row">
              <div>
                <strong>{{ row.equipmentName || '设备 #' + row.equipmentId }}</strong
                ><el-button link type="primary" @click="workflow.open('records', row)"
                  >详情</el-button
                >
              </div>
              <p>{{ row.description }}</p>
              <ol
                class="repair-progress"
                :aria-label="`当前状态：${repairStatusNames[row.status]}`"
              >
                <li
                  v-for="(label, index) in ['已提交', '已受理', '维修中', '已完成']"
                  :key="label"
                  :class="{ done: index <= progress(row.status) }"
                >
                  {{ label }}
                </li>
              </ol>
            </article>
          </div>
          <StateEmpty
            v-else
            title="还没有报修记录"
            description="发现故障时，可在设备页提交报修"
            compact
          />
        </section>
      </div>
      <aside class="student-tip">
        <el-icon><Calendar /></el-icon>
        <div>
          <strong>预约小提醒</strong>
          <p>审核通过后，请在预约开始后30分钟内、且结束前签到；无法到场时请提前取消。</p>
        </div>
        <el-button text :icon="RefreshRight" :loading="loading" @click="refresh"
          >刷新记录</el-button
        >
      </aside>
      <ReservationGuide ref="reservationGuide" />
    </template>
    <EquipmentPreview
      v-model="previewOpen"
      :equipment="selectedEquipment"
      :profile="user.user"
      @action="previewAction"
    />
    <WorkflowDialog ref="workflow" @success="refresh" />
  </div>
</template>
