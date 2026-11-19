import { resourceDefinitions, roleNames } from '@/resources'

import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getLabPage, deleteLab } from '@/api/lab'
import { getEquipmentPage, deleteEquipment } from '@/api/equipment'
import { getMyReservations, getReservationPage } from '@/api/reservation'
import { getMyRepairs, getRepairPage } from '@/api/repair'
import { getUserPage } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { rowActions } from '@/utils/workflow'
import { getToken } from '@/utils/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useResourceTable() {
  const route = useRoute()
  const router = useRouter()
  const user = useUserStore()
  const resource = computed(() => route.meta.resource)
  const filters = reactive({ keyword: '', status: '', role: '' })
  const page = reactive({ current: 1, size: 10 })
  const rows = ref([])
  const total = ref(0)
  const loaded = ref(false)
  const loading = ref(false)
  const error = ref('')
  const validationError = ref('')
  const previewOpen = shallowRef(false)
  const selectedEquipment = shallowRef(null)
  function inspectEquipment(row) {
    selectedEquipment.value = row
    previewOpen.value = true
  }
  function previewAction({ action, row }) {
    workflow.value?.open(action, row)
  }
  function filterStatus(value) {
    filters.status = value
    search()
  }
  const reservationDetail = ref(null)
  const workflow = ref(null),
    crud = ref(null),
    repairScope = ref('managed'),
    managedLabs = ref([])
  // 助手的一次性入口仅打开表单，提交仍走原有校验和用户确认。
  watch(
    [() => route.query.action, workflow, () => user.role],
    async ([action, dialog]) => {
      if (!dialog || !action) return
      const allowed =
        (action === 'apply' && resource.value === 'reservation' && user.role === 'student') ||
        (action === 'submit' &&
          resource.value === 'repair' &&
          ['student', 'admin', 'lab_admin'].includes(user.role))
      const token = getToken(),
        role = user.role,
        path = route.path,
        query = { ...route.query }
      delete query.action
      await router.replace({ path, query })
      if (allowed && token === getToken() && role === user.role && path === route.path)
        dialog.open(action)
    },
    { flush: 'post' },
  )
  const canCreate = computed(() =>
    resource.value === 'lab' ? user.isAdmin : resource.value === 'equipment' && user.isManager,
  )
  function canManage(row) {
    return (
      user.isAdmin ||
      (resource.value === 'equipment' &&
        user.role === 'lab_admin' &&
        managedLabs.value.includes(row.labId))
    )
  }
  async function deleteRow(row) {
    const token = getToken(),
      kind = resource.value
    try {
      await ElMessageBox.confirm(
        '确定删除该记录？关联预约或工单存在时系统会阻止删除。',
        '删除确认',
        { type: 'warning' },
      )
      if (token !== getToken() || kind !== resource.value || !canManage(row)) return
      await (kind === 'lab' ? deleteLab : deleteEquipment)(row.id)
      if (token === getToken()) {
        ElMessage.success('已删除')
        fetchRows()
      }
    } catch (e) {
      if (e !== 'cancel' && e !== 'close' && token === getToken()) error.value = e.message
    }
  }
  watch(
    () => user.user?.id,
    async () => {
      if (user.role !== 'lab_admin') return
      const token = getToken()
      try {
        const all = []
        for (let current = 1; current <= 100; current++) {
          const d = await getLabPage({ current, size: 100, adminId: user.user.id })
          if (token !== getToken()) return
          all.push(...d.records.map((l) => l.id))
          if (current * 100 >= d.total) break
        }
        managedLabs.value = all
      } catch {
        managedLabs.value = []
      }
    },
    { immediate: true },
  )
  const currentTime = ref(Date.now())
  const compact = ref(window.innerWidth <= 600)
  function resize() {
    compact.value = window.innerWidth <= 600
  }
  window.addEventListener('resize', resize)
  const clockTimer = setInterval(() => {
    currentTime.value = Date.now()
  }, 15000)
  const actionsFor = (row) =>
    rowActions(
      resource.value,
      row,
      resource.value === 'repair' && repairScope.value === 'mine'
        ? { ...user.user, role: 'student' }
        : user.user,
      currentTime.value,
    )
  function workflowSucceeded({ action }) {
    if (action === 'submit' && user.isManager) repairScope.value = 'mine'
    if (action === 'apply' || action === 'submit') {
      filters.status = ''
      appliedFilters = { ...appliedFilters }
      delete appliedFilters.status
      page.current = 1
    }
    fetchRows()
  }
  let requestId = 0
  let appliedFilters = {}
  const config = computed(() => resourceDefinitions[resource.value])
  const columns = computed(() =>
    resource.value === 'equipment'
      ? [
          ...config.value.columns.filter((item) => item[0] === 'name'),
          ...config.value.columns.filter((item) => item[0] !== 'name'),
        ]
      : config.value.columns,
  )
  const scopeLabel = computed(() =>
    ['reservation', 'repair'].includes(resource.value)
      ? user.isManager && (resource.value !== 'repair' || repairScope.value === 'managed')
        ? '管理范围内的记录'
        : '我提交的记录'
      : '当前可见的记录',
  )
  function cellText(row, key) {
    const value = row[key]
    if (key === 'equipmentName') return value || `设备 #${row.equipmentId}`
    if (key === 'labName') return value || (row.labId ? `实验室 #${row.labId}` : '未提供')
    if (key === 'role') return roleNames[value] || value || '—'
    return value === null || value === undefined || value === ''
      ? '—'
      : String(value).replace(/^(\d{4}-\d{2}-\d{2})T(?=\d{2}:\d{2})/, '$1 ')
  }
  function statusInfo(value) {
    return (
      config.value.statuses.find(
        (item) => item[0] === Number(value) && value !== null && value !== '',
      ) || [value, '未知状态', 'info']
    )
  }
  async function fetchRows() {
    const id = ++requestId
    const token = getToken()
    loading.value = true
    error.value = ''
    rows.value = []
    total.value = 0
    loaded.value = false
    try {
      const fetcher = {
        lab: getLabPage,
        equipment: getEquipmentPage,
        reservation: user.isManager ? getReservationPage : getMyReservations,
        repair: user.isManager && repairScope.value === 'managed' ? getRepairPage : getMyRepairs,
        user: getUserPage,
      }[resource.value]
      if (!fetcher) throw new Error('此页面的资源类型不可用。')
      const data = await fetcher({ ...appliedFilters, current: page.current, size: page.size })
      if (id !== requestId || token !== getToken()) return
      if (
        !data ||
        !Array.isArray(data.records) ||
        !Number.isFinite(Number(data.total)) ||
        Number(data.total) < 0
      )
        throw new Error('服务返回的列表格式不正确，请稍后重试。')
      rows.value = data.records
      total.value = Number(data.total)
      loaded.value = true
      if (Number(data.current) > 0) page.current = Number(data.current)
      if (Number(data.size) > 0) page.size = Number(data.size)
    } catch (err) {
      if (id === requestId && token === getToken())
        error.value = err.response?.data?.message || err.message || '暂时无法加载记录，请稍后重试。'
    } finally {
      if (id === requestId) loading.value = false
    }
  }
  function search() {
    validationError.value = ''
    const keyword = filters.keyword.trim()
    if (
      keyword &&
      config.value.numeric &&
      (!/^[1-9]\d*$/.test(keyword) || !Number.isSafeInteger(Number(keyword)))
    ) {
      validationError.value = '设备 ID 须为有效的正整数。'
      return
    }
    appliedFilters = {}
    if (keyword)
      appliedFilters[config.value.searchKey] = config.value.numeric ? Number(keyword) : keyword
    if (filters.status !== '' && filters.status !== undefined && filters.status !== null)
      appliedFilters.status = filters.status
    if (resource.value === 'user' && filters.role) appliedFilters.role = filters.role
    page.current = 1
    fetchRows()
  }
  function reset() {
    filters.keyword = ''
    filters.status = ''
    filters.role = ''
    search()
  }
  function changePage(value) {
    page.current = value
    fetchRows()
  }
  function changeSize(value) {
    page.size = value
    page.current = 1
    fetchRows()
  }
  watch(
    [resource, () => user.role, () => user.token, () => route.query.status],
    () => {
      filters.keyword = ''
      filters.status = ''
      filters.role = ''
      validationError.value = ''
      appliedFilters = {}
      const raw = route.query.status
      if (
        typeof raw === 'string' &&
        /^\d$/.test(raw) &&
        config.value?.statuses.some((item) => item[0] === Number(raw))
      ) {
        filters.status = Number(raw)
        appliedFilters.status = Number(raw)
      }
      page.current = 1
      page.size = 10
      fetchRows()
    },
    { immediate: true },
  )
  onBeforeUnmount(() => {
    requestId++
    clearInterval(clockTimer)
    window.removeEventListener('resize', resize)
  })

  return {
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
    getAppliedFilters: () => ({ ...appliedFilters }),
  }
}
