<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNotificationCenter } from '@/composables/useNotificationCenter'
import StateEmpty from '@/components/common/StateEmpty.vue'
import StateError from '@/components/common/StateError.vue'
import StateLoading from '@/components/common/StateLoading.vue'

const route = useRoute()
const user = useUserStore()
const mode = computed(() => route.meta.notificationMode)
const manager = computed(() => mode.value === 'manager')
const { state, load, act } = useNotificationCenter(mode, user)
</script>

<template>
  <div class="notification-page">
    <div class="page-heading">
      <div>
        <h1>{{ manager ? '联系待办' : '我的通知' }}</h1>
        <p class="page-description">
          {{
            manager
              ? '查看临近且尚未确认的故障取消预约，并记录人工联系情况。'
              : '查看设备故障影响预约的通知，并确认已知晓。'
          }}
        </p>
      </div>
      <el-button :disabled="state.loading" @click="load()">刷新</el-button>
    </div>
    <p v-if="manager" class="notification-hint">
      当前显示开始时间在 24 小时内、尚未结束、学生未确认且未记录人工联系的预约。
      标记已联系不会代替学生确认。
    </p>
    <StateLoading v-if="state.loading" label="正在加载通知…" :rows="4" />
    <StateError v-else-if="state.error" :description="state.error" @retry="load()" />
    <StateEmpty
      v-else-if="!state.records.length"
      :title="manager ? '暂无待联系事项' : '暂无通知'"
      :description="
        manager ? '当前没有符合条件的故障取消预约。' : '设备故障影响到预约时，通知会显示在这里。'
      "
    />
    <div v-else class="notification-list">
      <article v-for="item in state.records" :key="item.id" class="notification-item">
        <div class="notification-main">
          <div class="notification-top">
            <strong>{{ item.equipmentName || `设备 ${item.equipmentId}` }}</strong>
            <span>{{ item.confirmedAt ? '已确认' : '待确认' }}</span>
          </div>
          <p>{{ item.content || '因设备故障取消预约，请重新安排使用时间。' }}</p>
          <p class="notification-meta">预约时间：{{ item.startTime }} 至 {{ item.endTime }}</p>
          <p v-if="manager" class="notification-meta">
            学生：{{ item.studentName || `用户 ${item.studentId}` }}
            <template v-if="item.studentPhone"> · 电话：{{ item.studentPhone }}</template>
          </p>
          <p v-if="manager && item.labId == null" class="notification-meta">
            历史通知未记录事件发生时的实验室，请核实归属。
          </p>
          <p v-if="manager" class="notification-meta">
            {{ item.deliveryStatus === 1 ? '站内已可见' : '尚未站内可见' }}
          </p>
          <p v-if="item.createdAt" class="notification-meta">事件创建时间：{{ item.createdAt }}</p>
        </div>
        <el-button
          v-if="manager || !item.confirmedAt"
          type="primary"
          plain
          :loading="state.actingId === item.id"
          :disabled="state.actingId !== null"
          @click="act(item.id)"
          >{{ manager ? '标记已联系' : '确认已知晓' }}</el-button
        >
      </article>
      <el-pagination
        v-if="state.total > state.size"
        :current-page="state.current"
        :page-size="state.size"
        :total="state.total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.notification-hint {
  color: var(--ink-600, #526572);
  line-height: 1.6;
  margin-bottom: 16px;
}
.notification-list {
  display: grid;
  gap: 12px;
}
.notification-item {
  display: flex;
  gap: 20px;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--line-200, #e2e9ed);
  border-radius: 12px;
}
.notification-main {
  min-width: 0;
}
.notification-top {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.notification-top span,
.notification-meta {
  color: var(--ink-600, #526572);
  font-size: 13px;
}
.notification-item p {
  margin: 8px 0 0;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  .notification-item {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
