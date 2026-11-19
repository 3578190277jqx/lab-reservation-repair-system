<script setup>
import { useUserStore } from '@/stores/user'
import { useOperationLogs } from '@/composables/useOperationLogs'
import StateEmpty from '@/components/common/StateEmpty.vue'
import StateError from '@/components/common/StateError.vue'
import StateLoading from '@/components/common/StateLoading.vue'
const user = useUserStore()
const { filters, page, rows, total, loading, error, load, search, reset, changePage, changeSize } =
  useOperationLogs(() => user.role)
const columns = [
  ['operatorName', '操作人', 130],
  ['module', '模块', 130],
  ['action', '操作', 150],
  ['requestMethod', '方法', 90],
  ['requestPath', '接口路径', 220],
  ['targetId', '目标ID', 110],
  ['ip', 'IP地址', 140],
  ['traceId', '链路编号', 240],
  ['resultCode', '响应码', 90],
  ['durationMs', '耗时(ms)', 100],
  ['createTime', '操作时间', 180],
]
function cell(row, key) {
  return row[key] == null || row[key] === ''
    ? '—'
    : String(row[key]).replace(/^(\d{4}-\d{2}-\d{2})T/, '$1 ')
}
</script>
<template>
  <div v-if="user.isAdmin" class="page-stack">
    <header class="page-heading">
      <div>
        <span class="section-kicker">OPERATION LOGS</span>
        <h1>操作审计</h1>
        <p class="page-description">核对管理操作、执行结果和链路编号，辅助问题追溯。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>
    <section class="surface-card resource-panel" aria-label="操作审计记录">
      <el-form class="filters-bar" label-position="top" @submit.prevent="search">
        <el-form-item label="模块">
          <el-select v-model="filters.module" clearable placeholder="全部模块">
            <el-option label="用户管理" value="user" />
            <el-option label="实验室管理" value="lab" />
            <el-option label="设备管理" value="equipment" />
            <el-option label="预约管理" value="reservation" />
            <el-option label="故障报修" value="repair" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行结果"
          ><el-select v-model="filters.success" clearable placeholder="全部结果"
            ><el-option label="成功" :value="1" /><el-option label="失败" :value="0" /></el-select
        ></el-form-item>
        <div class="filter-actions">
          <el-button type="primary" native-type="submit" :loading="loading">查询</el-button
          ><el-button :disabled="loading" @click="reset">重置</el-button>
        </div>
      </el-form>
      <StateError v-if="error" :description="error" @retry="load" />
      <StateLoading v-else-if="loading" :rows="5" />
      <template v-else>
        <div class="list-summary">
          <h2>管理操作记录</h2>
          <span class="record-count">共 {{ total }} 条</span>
        </div>
        <el-table :data="rows" row-key="id" class="resource-table">
          <el-table-column
            v-for="column in columns"
            :key="column[0]"
            :label="column[1]"
            :min-width="column[2]"
            show-overflow-tooltip
            ><template #default="{ row }">{{ cell(row, column[0]) }}</template></el-table-column
          >
          <el-table-column label="结果" width="100"
            ><template #default="{ row }"
              ><el-tag :type="Number(row.success) === 1 ? 'success' : 'danger'">{{
                Number(row.success) === 1 ? '成功' : '失败'
              }}</el-tag></template
            ></el-table-column
          >
          <template #empty
            ><StateEmpty title="暂无审计记录" description="执行管理操作后可在此核对结果" compact
          /></template>
        </el-table>
        <div class="pagination-bar">
          <span class="pagination-hint">第 {{ page.current }} 页</span
          ><el-pagination
            :current-page="page.current"
            :page-size="page.size"
            :page-sizes="[20, 50, 100]"
            :total="total"
            layout="sizes, prev, pager, next"
            background
            @current-change="changePage"
            @size-change="changeSize"
          />
        </div>
      </template>
    </section>
  </div>
</template>
