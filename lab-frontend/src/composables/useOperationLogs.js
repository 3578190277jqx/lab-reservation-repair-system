import { onBeforeUnmount, reactive, shallowRef, watch, toValue } from 'vue'
import { getOperationLogPage } from '@/api/operation-log'
import { getToken } from '@/utils/auth'

export function useOperationLogs(role) {
  const filters = reactive({ module: '', success: '' })
  const page = reactive({ current: 1, size: 20 })
  const rows = shallowRef([]),
    total = shallowRef(0),
    loading = shallowRef(false),
    error = shallowRef('')
  let requestId = 0
  let appliedFilters = {}
  async function load() {
    const id = ++requestId,
      token = getToken()
    rows.value = []
    total.value = 0
    error.value = ''
    if (toValue(role) !== 'admin') {
      loading.value = false
      return
    }
    loading.value = true
    try {
      const data = await getOperationLogPage({ ...appliedFilters, ...page })
      if (id !== requestId || token !== getToken() || toValue(role) !== 'admin') return
      if (
        !Array.isArray(data?.records) ||
        !Number.isFinite(Number(data.total)) ||
        Number(data.total) < 0
      )
        throw new Error('服务返回的审计列表格式异常')
      rows.value = data.records
      total.value = Number(data.total)
    } catch (err) {
      if (id === requestId && token === getToken()) error.value = err.message || '审计日志加载失败'
    } finally {
      if (id === requestId) loading.value = false
    }
  }
  function search() {
    appliedFilters = {}
    const module = String(filters.module ?? '').trim()
    if (module) appliedFilters.module = module
    if (filters.success !== '' && filters.success != null)
      appliedFilters.success = Number(filters.success)
    page.current = 1
    return load()
  }
  function reset() {
    filters.module = ''
    filters.success = ''
    return search()
  }
  function changePage(value) {
    page.current = value
    return load()
  }
  function changeSize(value) {
    page.size = value
    page.current = 1
    return load()
  }
  watch(
    () => toValue(role),
    () => {
      page.current = 1
      load()
    },
    { immediate: true },
  )
  onBeforeUnmount(() => {
    requestId++
  })
  return { filters, page, rows, total, loading, error, load, search, reset, changePage, changeSize }
}
