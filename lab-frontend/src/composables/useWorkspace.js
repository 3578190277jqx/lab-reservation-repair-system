import { onBeforeUnmount, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'
import { getWorkspace } from '@/api/workspace'
import { getRepairPage } from '@/api/repair'

export function useWorkspace() {
  const user = useUserStore(),
    data = ref({}),
    errors = ref({}),
    loading = ref(false)
  const repairStatus = ref(0),
    repairsLoading = ref(false)
  let sequence = 0,
    repairSequence = 0
  const current = (token, role) => !!token && token === getToken() && role === user.role
  async function refresh() {
    const id = ++sequence,
      repairId = ++repairSequence,
      token = getToken(),
      role = user.role
    errors.value = {}
    loading.value = false
    repairsLoading.value = false
    if (!token || !role) {
      data.value = {}
      return
    }
    loading.value = true
    repairsLoading.value = true
    try {
      const result = await getWorkspace(role, { repairStatus: repairStatus.value })
      if (id !== sequence || !current(token, role)) return
      // 旧首页请求可更新预约/概览，但不能覆盖新工单标签。
      for (const key of ['equipment', 'reservations', 'overview', 'repairs']) {
        if (key === 'repairs' && repairId !== repairSequence) continue
        const next = { ...data.value }
        delete next[key]
        if (result.data[key]) next[key] = result.data[key]
        data.value = next
        const nextErrors = { ...errors.value }
        delete nextErrors[key]
        if (result.errors[key]) nextErrors[key] = result.errors[key]
        errors.value = nextErrors
      }
    } catch (e) {
      if (id === sequence && current(token, role))
        errors.value = { ...errors.value, page: e.message }
    } finally {
      if (id === sequence) loading.value = false
      if (repairId === repairSequence) repairsLoading.value = false
    }
  }
  async function changeRepairStatus(status) {
    if (![0, 1, 2].includes(status) || !['admin', 'lab_admin'].includes(user.role)) return
    repairStatus.value = status
    const id = ++repairSequence,
      token = getToken(),
      role = user.role
    const next = { ...data.value }
    delete next.repairs
    data.value = next
    const nextErrors = { ...errors.value }
    delete nextErrors.repairs
    errors.value = nextErrors
    repairsLoading.value = false
    if (!token) return
    repairsLoading.value = true
    try {
      const result = await getRepairPage({ current: 1, size: 5, status })
      if (id !== repairSequence || !current(token, role)) return
      if (
        !result ||
        !Array.isArray(result.records) ||
        !Number.isFinite(Number(result.total)) ||
        Number(result.total) < 0
      )
        throw new Error('服务返回的数据格式不正确')
      data.value = { ...data.value, repairs: result }
    } catch (e) {
      if (id === repairSequence && current(token, role))
        errors.value = { ...errors.value, repairs: e.message || '工单加载失败' }
    } finally {
      if (id === repairSequence) repairsLoading.value = false
    }
  }
  watch(
    [() => user.token, () => user.role],
    () => {
      data.value = {}
      errors.value = {}
      repairStatus.value = 0
      refresh()
    },
    { immediate: true },
  )
  onBeforeUnmount(() => {
    sequence++
    repairSequence++
  })
  return { data, errors, loading, refresh, repairStatus, repairsLoading, changeRepairStatus }
}
