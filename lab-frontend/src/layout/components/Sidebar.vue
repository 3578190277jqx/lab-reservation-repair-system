<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  House,
  OfficeBuilding,
  Monitor,
  Calendar,
  Checked,
  Tools,
  DataAnalysis,
  User,
  ArrowRight,
  ChatDotRound,
  DocumentChecked,
  Bell,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { roleTitle } from '@/utils/role-ui'

defineProps({ collapsed: Boolean })
const emit = defineEmits(['navigate'])
const route = useRoute()
const user = useUserStore()
const menus = computed(() => [
  { path: '/home', label: '工作台', icon: House },
  { path: '/labs', label: '实验室', icon: OfficeBuilding },
  { path: '/equipment', label: '设备资源', icon: Monitor },
  ...(user.role === 'student'
    ? [{ path: '/reservations', label: '我的预约', icon: Calendar }]
    : []),
  ...(user.isManager ? [{ path: '/reservation-review', label: '预约管理', icon: Checked }] : []),
  { path: '/repairs', label: user.isManager ? '报修管理' : '我的报修', icon: Tools },
  ...(user.isManager ? [{ path: '/contact-todos', label: '联系待办', icon: Bell }] : []),
  ...(user.isManager ? [{ path: '/stats', label: '统计分析', icon: DataAnalysis }] : []),
  ...(user.isAdmin ? [{ path: '/users', label: '用户管理', icon: User }] : []),
  ...(user.isAdmin ? [{ path: '/operation-logs', label: '操作审计', icon: DocumentChecked }] : []),
  { path: '/assistant', label: '操作助手', icon: ChatDotRound, DocumentChecked },
])
</script>

<template>
  <div class="sidebar-content">
    <RouterLink
      to="/home"
      class="sidebar-brand"
      aria-label="知行实验室，返回工作台"
      @click="emit('navigate')"
    >
      <span class="brand-symbol"><span></span><span></span><span></span><span></span></span>
      <span v-if="!collapsed" class="brand-wordmark"
        ><strong>知行实验室</strong><small>实验室管理平台</small></span
      >
    </RouterLink>
    <div v-if="!collapsed" class="nav-caption">资源与业务管理</div>
    <el-menu
      :default-active="route.path"
      :collapse="collapsed"
      :collapse-transition="false"
      router
      class="workspace-menu"
      @select="emit('navigate')"
    >
      <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title
          ><span>{{ roleTitle(item.path, user.role, item.label) }}</span></template
        >
      </el-menu-item>
    </el-menu>
    <div class="sidebar-bottom" :class="{ compact: collapsed }">
      <span class="live-dot"></span><span v-if="!collapsed">让每一次探索井然有序</span>
    </div>
    <RouterLink v-if="!collapsed" class="sidebar-profile" to="/profile" @click="emit('navigate')">
      <span class="avatar-small">{{ (user.displayName || '用').slice(0, 1) }}</span>
      <span
        ><strong>{{ user.displayName }}</strong
        ><small>查看个人信息</small></span
      >
      <el-icon><ArrowRight /></el-icon>
    </RouterLink>
  </div>
</template>
