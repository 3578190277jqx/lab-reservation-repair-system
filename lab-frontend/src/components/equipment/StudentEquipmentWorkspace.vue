<script setup>
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import { useResourceTable } from '@/composables/useResourceTable'
import { useWorkflowDialog } from '@/composables/useWorkflowDialog'
import { useReservationSchedule } from '@/composables/useReservationSchedule'
import { getToken } from '@/utils/auth'
import EquipmentList from './EquipmentList.vue'
import EquipmentPlanningPanel from './EquipmentPlanningPanel.vue'
import ResourceFilters from '@/components/resources/ResourceFilters.vue'
import WorkflowDialog from '@/components/WorkflowDialog.vue'
import StateError from '@/components/common/StateError.vue'
import StateLoading from '@/components/common/StateLoading.vue'
import StateEmpty from '@/components/common/StateEmpty.vue'

const table = useResourceTable()
const { user, config, filters, page, rows, total, loaded, loading, error } = table
const workspaceToken = user.token
const selected = shallowRef(null),
  panelOpen = ref(false),
  filtersOpen = ref(false)
const wide = ref(window.innerWidth >= 1200),
  panel = ref(null),
  report = ref(null)
function resize() {
  wide.value = window.innerWidth >= 1200
}
window.addEventListener('resize', resize)
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  // 会话失效可能先触发组件卸载，不能仅依赖稍后执行的身份监听清理提示框。
  if (confirming) ElMessageBox.close()
})
const booking = useWorkflowDialog({
  onSuccess: async () => {
    await table.fetchRows()
    if (selected.value)
      selected.value = rows.value.find((row) => row.id === selected.value.id) || selected.value
    schedule.load()
  },
})
const scheduleInput = computed(() => ({
  equipmentId: selected.value?.id,
  startTime: booking.visible.value ? booking.form.times?.[0] : '',
  endTime: booking.visible.value ? booking.form.times?.[1] : '',
  refreshKey: booking.scheduleRevision.value,
}))
// 用 getter 保留响应式读取；控制器在工作区持有，不随桌面/抽屉切换卸载。
const schedule = useReservationSchedule({
  get equipmentId() {
    return scheduleInput.value.equipmentId
  },
  get startTime() {
    return scheduleInput.value.startTime
  },
  get endTime() {
    return scheduleInput.value.endTime
  },
  get refreshKey() {
    return scheduleInput.value.refreshKey
  },
})
const locked = computed(() => booking.busy.value || booking.uploading.value)
let confirming = false
async function discard() {
  if (locked.value || confirming) return false
  if (!booking.dirty.value) return true
  const token = user.token
  confirming = true
  try {
    await ElMessageBox.confirm(
      '当前预约尚未提交，离开后将清除已填写的时间和用途。',
      '放弃预约草稿？',
      { confirmButtonText: '放弃草稿', cancelButtonText: '继续填写', type: 'warning' },
    )
    return token === user.token && !locked.value
  } catch {
    return false
  } finally {
    confirming = false
  }
}
function guardDraftNavigation() {
  // 会话过期或切换账号时必须允许安全清理，不能让旧草稿阻止登录跳转。
  if (
    !workspaceToken ||
    user.token !== workspaceToken ||
    getToken() !== workspaceToken ||
    user.role !== 'student'
  )
    return true
  return discard()
}
onBeforeRouteLeave(guardDraftNavigation)
onBeforeRouteUpdate(guardDraftNavigation)
async function cancelBooking() {
  if (await discard()) booking.close()
}
async function select(row) {
  if (selected.value?.id !== row.id) {
    if (!(await discard())) return
    booking.reset()
    selected.value = row
  }
  panelOpen.value = true
  await nextTick()
  if (wide.value) panel.value?.focus({ preventScroll: true })
}
async function closePanel(done) {
  if (!(await discard())) return
  booking.reset()
  panelOpen.value = false
  selected.value = null
  done?.()
}
async function changeList(fn) {
  if (!(await discard())) return
  booking.reset()
  selected.value = null
  panelOpen.value = false
  fn()
}
function statusChanged(value) {
  changeList(() => table.filterStatus(value))
}
async function reportEquipment(row) {
  if (!(await discard())) return
  booking.reset()
  panelOpen.value = false
  // 等抽屉完全关闭再打开报修，避免两个模态层叠加。
  if (wide.value) report.value?.open('submit', row)
  else queuedReport.value = row
}
const queuedReport = shallowRef(null)
function drawerClosed() {
  if (!queuedReport.value) return
  const row = queuedReport.value
  queuedReport.value = null
  report.value?.open('submit', row)
}
watch(wide, async (isWide) => {
  if (!isWide) return
  // 断点切换会直接卸载抽屉，不能只依赖其 closed 事件消费报修意图。
  // DOM 更新后抽屉已移除，才允许打开报修弹窗。
  await nextTick()
  drawerClosed()
})
function reportSucceeded() {
  selected.value = null
  table.fetchRows()
}
watch([() => user.token, () => user.role], () => {
  if (confirming) ElMessageBox.close()
  selected.value = null
  panelOpen.value = false
  queuedReport.value = null
})
</script>
<template>
  <div class="equipment-workspace">
    <header class="equipment-heading">
      <div>
        <h1>找设备</h1>
        <p>选择实验设备，查看占用排期并申请使用。</p>
      </div>
      <el-button
        :icon="RefreshRight"
        :loading="loading"
        :disabled="locked"
        @click="changeList(table.fetchRows)"
        >刷新</el-button
      >
    </header>
    <section class="equipment-search" aria-label="设备搜索与筛选">
      <el-button
        class="mobile-filter-toggle"
        :aria-expanded="filtersOpen"
        aria-controls="equipment-filters"
        @click="filtersOpen = !filtersOpen"
        >{{ filtersOpen ? '收起筛选' : '搜索与筛选' }}</el-button
      >
      <div id="equipment-filters" :class="{ 'filters-expanded': filtersOpen }">
        <ResourceFilters
          v-model:keyword="filters.keyword"
          v-model:status="filters.status"
          :config="config"
          resource="equipment"
          :loading="loading || locked"
          @search="changeList(table.search)"
          @reset="changeList(table.reset)"
          @status-change="statusChanged"
        />
      </div>
    </section>
    <div class="equipment-work-columns">
      <section class="equipment-list-panel" aria-label="设备列表" :aria-busy="loading">
        <header class="list-label">
          <h2>设备清单</h2>
          <span v-if="loaded">{{ total.toLocaleString('zh-CN') }} 台符合条件</span>
        </header>
        <StateError
          v-if="error"
          :description="error"
          :disabled="loading"
          @retry="table.fetchRows"
        />
        <StateLoading v-else-if="loading" :rows="4" />
        <EquipmentList
          v-else
          :rows="rows"
          :selected-id="selected?.id"
          :locked="locked"
          @select="select"
        />
        <footer v-if="loaded" class="equipment-pagination">
          <span>第 {{ page.current }} 页</span
          ><el-pagination
            :current-page="page.current"
            :page-size="page.size"
            :total="total"
            :pager-count="5"
            layout="prev, pager, next"
            :disabled="locked || loading"
            @current-change="(value) => changeList(() => table.changePage(value))"
          />
        </footer>
      </section>
      <aside
        v-if="wide"
        ref="panel"
        class="equipment-planning"
        tabindex="-1"
        aria-label="所选设备与排期"
      >
        <EquipmentPlanningPanel
          v-if="selected"
          :equipment="selected"
          :profile="user.user"
          :booking="booking"
          :schedule="schedule"
          @report="reportEquipment"
          @cancel-booking="cancelBooking"
        />
        <StateEmpty
          v-else
          title="先选择一台设备"
          description="点击左侧“查看排期”，查看这台设备的占用时段，再填写预约。"
          compact
        />
      </aside>
    </div>
    <el-drawer
      v-if="!wide"
      :model-value="panelOpen"
      title="设备排期与预约"
      size="min(520px, 100vw)"
      :before-close="closePanel"
      :close-on-click-modal="false"
      :close-on-press-escape="!locked"
      :show-close="!locked"
      append-to-body
      @closed="drawerClosed"
    >
      <EquipmentPlanningPanel
        v-if="selected"
        :equipment="selected"
        :profile="user.user"
        :booking="booking"
        :schedule="schedule"
        @report="reportEquipment"
        @cancel-booking="cancelBooking"
      />
    </el-drawer>
    <WorkflowDialog ref="report" @success="reportSucceeded" />
  </div>
</template>
<style scoped>
.equipment-workspace {
  display: grid;
  gap: var(--sp-2);
}
.equipment-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-2);
}
h1 {
  margin: 0;
  color: var(--ink-900);
  font-size: var(--fs-xl);
  line-height: 1.4;
}
.equipment-heading p {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
}
.equipment-search {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  overflow: hidden;
}
.equipment-search :deep(.filters-bar) {
  padding: var(--sp-2);
  margin: 0;
  gap: var(--sp-2);
}
.equipment-search :deep(.status-filter) {
  padding: 0 var(--sp-2) var(--sp-2);
  margin: 0;
}
.equipment-work-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(320px, 1fr);
  gap: var(--sp-2);
  align-items: start;
}
.equipment-list-panel,
.equipment-planning {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  min-width: 0;
}
.equipment-planning {
  padding: var(--sp-2);
}
.equipment-planning:focus-visible {
  outline: 2px solid var(--brand-600);
  outline-offset: 3px;
}
.list-label {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--sp-1);
  padding: var(--sp-2);
  border-bottom: 1px solid var(--line);
}
.list-label h2 {
  margin: 0;
  color: var(--ink-900);
  font-size: var(--fs-lg);
}
.list-label span,
.equipment-pagination > span {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  font-variant-numeric: tabular-nums;
}
.equipment-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-1);
  padding: var(--sp-2);
  border-top: 1px solid var(--line);
}
.mobile-filter-toggle {
  display: none;
}
@media (min-width: 1200px) {
  #equipment-filters {
    display: flex;
    align-items: flex-end;
    flex-wrap: wrap;
  }
  .equipment-search :deep(.filters-bar) {
    flex: 1 1 480px;
  }
  .equipment-search :deep(.keyword-filter) {
    width: 240px;
  }
}
@media (max-width: 1199px) {
  .equipment-work-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 699px) {
  h1 {
    font-size: var(--fs-xl);
  }
  .equipment-heading {
    align-items: flex-start;
  }
  .equipment-heading > .el-button {
    margin: 0;
  }
  .mobile-filter-toggle {
    display: flex;
    width: 100%;
    border: 0;
    justify-content: flex-start;
  }
  #equipment-filters {
    display: none;
  }
  #equipment-filters.filters-expanded {
    display: block;
  }
  .equipment-search :deep(.filters-bar .keyword-filter) {
    width: 100%;
  }
  .equipment-pagination :deep(.el-pager li),
  .equipment-pagination :deep(button) {
    min-width: 40px;
    min-height: 44px;
  }
}
</style>
