import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/stores'
import { useUserStore } from '@/stores/user'
import { getToken, onAuthExpired } from '@/utils/auth'
import { safeRedirect } from '@/utils/navigation'
import { roleTitle } from '@/utils/role-ui'

const managers = ['admin', 'lab_admin']
const listView = () => import('@/views/ResourceListView.vue')

export const routes = [
  {
    path: '/recover',
    name: 'recover',
    component: () => import('@/views/RecoveryView.vue'),
    meta: { title: '找回密码', public: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '学生注册', public: true },
  },
  {
    path: '/session-error',
    name: 'session-error',
    component: () => import('@/views/SessionErrorView.vue'),
    meta: { title: '连接失败', public: true },
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/ErrorView.vue'),
    meta: { title: '无访问权限', status: 403, public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'labs',
        name: 'labs',
        component: listView,
        meta: { title: '实验室', resource: 'lab' },
      },
      {
        path: 'equipment',
        name: 'equipment',
        component: () => import('@/views/EquipmentView.vue'),
        meta: { title: '设备管理', resource: 'equipment' },
      },
      {
        path: 'reservations',
        name: 'reservations',
        component: listView,
        meta: { title: '我的预约', resource: 'reservation', roles: ['student'] },
      },
      {
        path: 'reservation-review',
        name: 'reservation-review',
        component: listView,
        meta: { title: '预约审核', resource: 'reservation', roles: managers },
      },
      {
        path: 'repairs',
        name: 'repairs',
        component: listView,
        meta: { title: '故障工单', resource: 'repair' },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/NotificationView.vue'),
        meta: { title: '我的通知', roles: ['student'], notificationMode: 'student' },
      },
      {
        path: 'contact-todos',
        name: 'contact-todos',
        component: () => import('@/views/NotificationView.vue'),
        meta: { title: '联系待办', roles: managers, notificationMode: 'manager' },
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('@/views/StatsView.vue'),
        meta: { title: '数据统计', roles: managers },
      },
      {
        path: 'operation-logs',
        name: 'operation-logs',
        component: () => import('@/views/OperationLogView.vue'),
        meta: { title: '操作审计', roles: ['admin'] },
      },
      {
        path: 'users',
        name: 'users',
        component: listView,
        meta: { title: '用户管理', resource: 'user', roles: ['admin'] },
      },
      {
        path: 'assistant',
        name: 'assistant',
        component: () => import('@/views/AssistantView.vue'),
        meta: { title: '操作助手' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { title: '个人信息' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/ErrorView.vue'),
    meta: { title: '页面不存在', status: 404, public: true },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const store = useUserStore(pinia)
  // 登录页独立加载，后端未启动也可打开，避免守卫在登录页不断请求并重定向。
  if (to.meta.public) {
    if (['login', 'register'].includes(to.name) && getToken() && store.user) return '/home'
    return true
  }
  if (!getToken()) {
    store.resetSession()
    return {
      name: 'login',
      query: { redirect: safeRedirect(to.fullPath) || '/home' },
      replace: true,
    }
  }
  try {
    await store.fetchUserInfo()
  } catch (error) {
    if (error.code === 'ERR_AUTH_CHANGED') return false
    if (error.status === 401 || error.status === 403 || !getToken()) {
      store.resetSession()
      return {
        name: 'login',
        query: { redirect: safeRedirect(to.fullPath) || '/home' },
        replace: true,
      }
    }
    // 网络或5xx不清除有效令牌，给用户一个可重试的页面。
    return {
      name: 'session-error',
      query: { redirect: safeRedirect(to.fullPath) || '/home' },
      replace: true,
    }
  }
  if (to.matched.some((record) => record.meta.roles && !record.meta.roles.includes(store.role))) {
    return { name: 'forbidden', replace: true }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${roleTitle(to.path, useUserStore(pinia).role, to.meta.title || '首页')} · ${import.meta.env.VITE_APP_TITLE || '实验室预约与运维平台'}`
})

onAuthExpired(() => {
  const current = router.currentRoute.value
  const destination =
    safeRedirect(current.name === 'session-error' ? current.query.redirect : current.fullPath) ||
    '/home'
  useUserStore(pinia).resetSession()
  if (
    !router.currentRoute.value.meta.public ||
    router.currentRoute.value.name === 'session-error'
  ) {
    router.replace({ name: 'login', query: { redirect: destination } })
  }
})

export default router
