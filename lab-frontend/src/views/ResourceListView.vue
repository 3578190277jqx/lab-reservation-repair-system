<script setup>
import { RefreshRight } from '@element-plus/icons-vue'
import { roleTitle } from '@/utils/role-ui'
import { useResourceTable } from '@/composables/useResourceTable'
import WorkflowDialog from '@/components/WorkflowDialog.vue'
import ReservationDetail from '@/components/ReservationDetail.vue'
import AdminCrudDialog from '@/components/AdminCrudDialog.vue'
import EquipmentPreview from '@/components/interaction/EquipmentPreview.vue'
import StateError from '@/components/common/StateError.vue'
import StateLoading from '@/components/common/StateLoading.vue'
import ResourceFilters from '@/components/resources/ResourceFilters.vue'
import ResourceRecords from '@/components/resources/ResourceRecords.vue'
import EquipmentExcelActions from '@/components/resources/EquipmentExcelActions.vue'
const {
  route,
  user,
  resource,
  config,
  filters,
  page,
  rows,
  total,
  loaded,
  loading,
  error,
  validationError,
  previewOpen,
  selectedEquipment,
  inspectEquipment,
  previewAction,
  filterStatus,
  reservationDetail,
  workflow,
  crud,
  repairScope,
  canCreate,
  canManage,
  deleteRow,
  compact,
  actionsFor,
  workflowSucceeded,
  columns,
  scopeLabel,
  cellText,
  statusInfo,
  fetchRows,
  search,
  reset,
  changePage,
  changeSize,
  getAppliedFilters,
} = useResourceTable()

function changeRepairScope() {
  page.current = 1
  fetchRows()
}
</script>
<template>
  <div v-if="config" class="page-stack">
    <div class="page-heading">
      <div>
        <span class="section-kicker">{{ config.kicker }}</span>
        <h1>{{ roleTitle(route.path, user.role, route.meta.title) }}</h1>
        <p class="page-description">{{ config.description }}</p>
        <p v-if="resource === 'equipment' || resource === 'reservation'" class="page-description">
          {{
            user.role === 'student'
              ? '从设备列表申请预约；申请通过后按预约开始时间签到。'
              : '预约由学生账号申请，管理员在预约审核中处理；管理员也可提交故障报修。'
          }}
        </p>
      </div>
      <div class="workflow-toolbar">
        <EquipmentExcelActions
          :resource="resource"
          :role="user.role"
          :get-filters="getAppliedFilters"
          @imported="fetchRows"
        />
        <el-button v-if="canCreate" type="primary" @click="crud.open(resource)"
          >新增{{ resource === 'lab' ? '实验室' : '设备' }}</el-button
        >
        <el-button
          v-if="resource === 'reservation' && user.role === 'student'"
          type="primary"
          @click="workflow.open('apply')"
          >申请预约</el-button
        >
        <el-button v-if="resource === 'repair'" type="primary" @click="workflow.open('submit')"
          >提交报修</el-button
        >
        <el-button :icon="RefreshRight" :loading="loading" @click="fetchRows">刷新</el-button>
      </div>
    </div>
    <el-radio-group
      v-if="resource === 'repair' && user.isManager"
      v-model="repairScope"
      @change="changeRepairScope"
      ><el-radio-button value="managed">管理工单</el-radio-button
      ><el-radio-button value="mine">我的报修</el-radio-button></el-radio-group
    >
    <section class="surface-card resource-panel" :aria-label="`${route.meta.title}列表`">
      <ResourceFilters
        v-model:keyword="filters.keyword"
        v-model:status="filters.status"
        v-model:role="filters.role"
        :config="config"
        :resource="resource"
        :loading="loading"
        @search="search"
        @reset="reset"
        @status-change="filterStatus"
      />
      <el-alert
        v-if="validationError"
        :title="validationError"
        type="warning"
        :closable="false"
        show-icon
        class="list-alert"
      />
      <div class="list-summary">
        <h2>{{ scopeLabel }}</h2>
        <span v-if="loaded" class="record-count">共 {{ total.toLocaleString('zh-CN') }} 条</span
        ><span v-else-if="loading" class="muted-text">正在加载…</span>
      </div>
      <StateError v-if="error" :description="error" @retry="fetchRows" />
      <StateLoading v-else-if="loading" :rows="5" />
      <ResourceRecords
        v-else
        :resource="resource"
        :role="user.role"
        :rows="rows"
        :columns="columns"
        :compact="compact"
        :status-info="statusInfo"
        :cell-text="cellText"
        :actions-for="actionsFor"
        :can-manage="canManage"
        @inspect="inspectEquipment"
        @workflow="(action, row) => workflow.open(action, row)"
        @reservation-detail="(row) => reservationDetail.open(row)"
        @edit="(row, mode) => crud.open(resource, row, mode)"
        @delete="deleteRow"
      />
      <div v-if="loaded" class="pagination-bar">
        <span class="pagination-hint">第 {{ page.current }} 页 · 每页 {{ page.size }} 条</span
        ><el-pagination
          :current-page="page.current"
          :page-size="page.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="sizes, prev, pager, next"
          background
          :disabled="loading"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </section>
    <EquipmentPreview
      v-model="previewOpen"
      :equipment="selectedEquipment"
      :profile="user.user"
      @action="previewAction"
    />
    <ReservationDetail ref="reservationDetail" />
    <AdminCrudDialog ref="crud" @success="fetchRows" />
    <WorkflowDialog ref="workflow" @success="workflowSucceeded" />
  </div>
  <el-result
    v-else
    icon="warning"
    title="暂时无法打开此页面"
    sub-title="资源配置不可用，请返回工作台。"
    ><template #extra
      ><RouterLink to="/home"
        ><el-button type="primary">返回工作台</el-button></RouterLink
      ></template
    ></el-result
  >
</template>
<style scoped>
.workflow-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-1, 8px);
}
.workflow-toolbar .el-button + .el-button {
  margin-left: 0;
}
@media (max-width: 600px) {
  .workflow-toolbar {
    margin-top: var(--sp-2, 16px);
  }
}
</style>
