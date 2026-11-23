import { onBeforeUnmount, reactive, watch } from 'vue'
import {
  confirmNotification,
  getContactTodos,
  getMyNotifications,
  markContacted,
} from '@/api/notification'
import { getToken } from '@/utils/auth'

/** 账户或模式变化时丢弃旧请求，避免向新账户展示上一账户的数据。 */
export function useNotificationCenter(mode, user) {
  const state = reactive({
    records: [],
    total: 0,
    current: 1,
    size: 20,
    loading: false,
    error: '',
    actingId: null,
  })
  let version = 0

  function context() {
    return `${getToken()}|${user.role}|${mode.value}`
  }

  async function load(page = state.current) {
    const requestVersion = ++version
    const requestContext = context()
    state.current = page
    state.loading = true
    state.error = ''
    try {
      const fetchPage = mode.value === 'manager' ? getContactTodos : getMyNotifications
      let current = page
      let fallbacks = 0
      while (true) {
        const data = await fetchPage({ current, size: state.size })
        if (requestVersion !== version || requestContext !== context()) return
        if (!Array.isArray(data?.records) || !Number.isFinite(Number(data.total))) {
          throw new Error('通知数据格式异常，请重新加载')
        }
        const total = Number(data.total)
        const lastPage = Math.max(1, Math.ceil(total / state.size))
        if (current > lastPage) {
          // 列表可能在请求期间继续减少；有限次回退后交由用户重试。
          if (++fallbacks > 3) throw new Error('通知列表变化频繁，请重新加载')
          current = lastPage
          state.current = current
          continue
        }
        state.records = data.records
        state.total = total
        break
      }
    } catch (error) {
      if (requestVersion !== version || requestContext !== context()) return
      state.records = []
      state.total = 0
      state.error = error.message || '加载失败，请重试'
    } finally {
      if (requestVersion === version && requestContext === context()) state.loading = false
    }
  }

  async function act(id) {
    if (state.actingId !== null) return
    const requestContext = context()
    state.actingId = id
    try {
      const action = mode.value === 'manager' ? markContacted : confirmNotification
      await action(id)
      if (requestContext === context()) await load(state.current)
    } catch {
      // API 封装负责显示错误；保留当前内容，允许用户重试。
    } finally {
      if (requestContext === context()) state.actingId = null
    }
  }

  watch(
    () => [user.token, user.role, mode.value],
    () => {
      version++
      state.records = []
      state.total = 0
      state.actingId = null
      if (getToken()) load(1)
      else state.loading = false
    },
    { immediate: true },
  )
  onBeforeUnmount(() => {
    version++
  })

  return { state, load, act }
}
