<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Expand, Fold, ArrowDown, User, SwitchButton, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { roleTitle } from '@/utils/role-ui'

const props = defineProps({ collapsed: Boolean, mobile: Boolean, student: Boolean, navigationExpanded: Boolean })
const emit = defineEmits(['toggle', 'search'])
const shortcutLabel = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘ K' : 'Ctrl K'
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const signingOut = ref(false)
const roleName = computed(() => ({ admin: '系统管理员', lab_admin: '实验室管理员', student: '学生' }[user.role] || '用户'))
async function command(value) {
  if (value === 'profile') return router.push('/profile')
  if (value !== 'logout' || signingOut.value) return
  try { await ElMessageBox.confirm('退出后需要重新登录才能访问工作空间。', '确认退出登录？', { confirmButtonText: '退出登录', cancelButtonText: '继续使用', type: 'warning' }) } catch { return }
  signingOut.value = true
  try { await user.logout(); await router.replace('/login') } catch { /* 请求层已显示错误 */ } finally { signingOut.value = false }
}
</script>

<template>
  <header class="topbar">
    <RouterLink v-if="student" to="/home" class="student-wordmark"><span class="brand-symbol"><span></span><span></span><span></span><span></span></span><strong>知行实验室<small>学生服务 · 预约与探索</small></strong></RouterLink>
    <div v-else class="topbar-left">
      <el-button text class="sidebar-toggle" :aria-expanded="navigationExpanded" :aria-controls="mobile ? undefined : 'workspace-sidebar'" :aria-label="props.mobile ? '打开导航菜单' : collapsed ? '展开导航菜单' : '收起导航菜单'" @click="emit('toggle')"><el-icon :size="21"><Expand v-if="collapsed || mobile" /><Fold v-else /></el-icon></el-button>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/home' }">运维中心</el-breadcrumb-item>
        <el-breadcrumb-item>{{ roleTitle(route.path,user.role,route.meta.title) }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="topbar-right">
      <button type="button" class="quick-search-button" aria-label="搜索页面和操作" aria-haspopup="dialog" @click="emit('search')"><el-icon><Search /></el-icon><span>快捷搜索</span><kbd>{{ shortcutLabel }}</kbd></button>
      <span class="topbar-role">{{ roleName }}</span>
      <el-dropdown trigger="click" @command="command">
        <button class="account-button" :disabled="signingOut" aria-label="打开账户菜单">
          <span class="avatar-small avatar-light">{{ (user.displayName || '用').slice(0, 1) }}</span>
          <span class="account-name">{{ user.displayName }}</span><el-icon><ArrowDown /></el-icon>
        </button>
        <template #dropdown><el-dropdown-menu>
          <el-dropdown-item command="profile" :icon="User">个人信息</el-dropdown-item>
          <el-dropdown-item command="logout" :icon="SwitchButton" divided :disabled="signingOut">退出登录</el-dropdown-item>
        </el-dropdown-menu></template>
      </el-dropdown>
    </div>
  </header>
</template>
