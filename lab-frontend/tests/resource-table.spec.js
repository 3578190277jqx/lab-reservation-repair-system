import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import { deferred, mountComposable } from './helpers/composable'
const mocks = vi.hoisted(() => ({
  route: null,
  user: null,
  token: 'token',
  replace: vi.fn(),
  lab: vi.fn(),
  equipment: vi.fn(),
  reservation: vi.fn(),
  myReservation: vi.fn(),
  repair: vi.fn(),
  myRepair: vi.fn(),
  users: vi.fn(),
  deleteLab: vi.fn(),
  deleteEquipment: vi.fn(),
  confirm: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => mocks.user }))
vi.mock('@/utils/auth', () => ({ getToken: () => mocks.token }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn() },
  ElMessageBox: { confirm: mocks.confirm },
}))
vi.mock('@/api/lab', () => ({ getLabPage: mocks.lab, deleteLab: mocks.deleteLab }))
vi.mock('@/api/equipment', () => ({
  getEquipmentPage: mocks.equipment,
  deleteEquipment: mocks.deleteEquipment,
}))
vi.mock('@/api/reservation', () => ({
  getReservationPage: mocks.reservation,
  getMyReservations: mocks.myReservation,
}))
vi.mock('@/api/repair', () => ({ getRepairPage: mocks.repair, getMyRepairs: mocks.myRepair }))
vi.mock('@/api/user', () => ({ getUserPage: mocks.users }))
import { useResourceTable } from '@/composables/useResourceTable'
let mounted
beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers()
  mocks.token = 'token'
  vi.stubGlobal('window', Object.assign(new EventTarget(), { innerWidth: 1200 }))
  mocks.route = reactive({ path: '/equipment', meta: { resource: 'equipment' }, query: {} })
  mocks.user = reactive({
    user: { id: 7, role: 'admin' },
    token: 'token',
    get role() {
      return this.user.role
    },
    get isAdmin() {
      return this.role === 'admin'
    },
    get isManager() {
      return ['admin', 'lab_admin'].includes(this.role)
    },
  })
  for (const name of [
    'lab',
    'equipment',
    'reservation',
    'myReservation',
    'repair',
    'myRepair',
    'users',
  ])
    mocks[name].mockResolvedValue({ records: [{ id: 1 }], total: 1, current: 1, size: 10 })
  mocks.replace.mockResolvedValue()
  mocks.confirm.mockResolvedValue(true)
})
afterEach(() => {
  mounted?.unmount()
  mounted = null
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
function mount(resource = 'equipment', role = 'admin') {
  mocks.route.meta.resource = resource
  mocks.user.user.role = role
  mounted = mountComposable(useResourceTable)
  return mounted.result
}
async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
describe('resource table refactor retains five resource contracts', () => {
  it.each([
    ['lab', 'lab'],
    ['equipment', 'equipment'],
    ['reservation', 'reservation'],
    ['repair', 'repair'],
    ['user', 'users'],
  ])('loads %s with its matching page service', async (kind, service) => {
    const f = mount(kind)
    await flush()
    expect(mocks[service]).toHaveBeenCalledWith({ current: 1, size: 10 })
    expect(f.rows.value).toEqual([{ id: 1 }])
    expect(f.loaded.value).toBe(true)
    expect(f.config.value.columns.length).toBeGreaterThan(0)
  })
  it.each([
    ['reservation', 'myReservation'],
    ['repair', 'myRepair'],
  ])('student %s uses the personal endpoint', async (kind, service) => {
    mount(kind, 'student')
    await flush()
    expect(mocks[service]).toHaveBeenCalledOnce()
    expect(mocks[kind]).not.toHaveBeenCalled()
  })
  it('uses applied rather than half-entered filters for export and pagination', async () => {
    const f = mount()
    await flush()
    f.filters.keyword = ' 示波器 '
    f.filters.status = 0
    f.search()
    await flush()
    f.filters.keyword = '未提交查询'
    expect(f.getAppliedFilters()).toEqual({ name: '示波器', status: 0 })
    f.changePage(2)
    await flush()
    expect(mocks.equipment).toHaveBeenLastCalledWith({
      name: '示波器',
      status: 0,
      current: 2,
      size: 10,
    })
  })
  it('validates numeric equipment ID before requesting reservations', async () => {
    const f = mount('reservation')
    await flush()
    mocks.reservation.mockClear()
    f.filters.keyword = '设备 abc'
    f.search()
    expect(f.validationError.value).toContain('正整数')
    expect(mocks.reservation).not.toHaveBeenCalled()
    f.filters.keyword = '12'
    f.search()
    await flush()
    expect(mocks.reservation).toHaveBeenCalledWith({ equipmentId: 12, current: 1, size: 10 })
  })
  it('keeps a later query when an earlier request completes last', async () => {
    const first = deferred()
    mocks.equipment.mockReturnValueOnce(first.promise)
    const f = mount()
    f.filters.keyword = '示波器'
    f.search()
    await flush()
    first.resolve({ records: [{ id: 99 }], total: 1 })
    await flush()
    expect(f.rows.value).toEqual([{ id: 1 }])
  })
  it('does not assign a response from a previous login', async () => {
    const delivery = deferred()
    mocks.equipment.mockReturnValue(delivery.promise)
    const f = mount()
    mocks.token = 'new-token'
    delivery.resolve({ records: [{ id: 99 }], total: 1 })
    await flush()
    expect(f.rows.value).toEqual([])
  })
  it('resets filters when changing between resources and does not send stale fields', async () => {
    const f = mount('user')
    await flush()
    f.filters.keyword = 'alice'
    f.filters.role = 'student'
    f.search()
    await flush()
    mocks.route.meta.resource = 'lab'
    await flush()
    expect(mocks.lab).toHaveBeenLastCalledWith({ current: 1, size: 10 })
    expect(f.filters.role).toBe('')
    expect(f.config.value.kicker).toBe('LABORATORIES')
  })
  it('keeps manager personal report scope after creating a repair', async () => {
    const f = mount('repair')
    await flush()
    f.workflowSucceeded({ action: 'submit' })
    await flush()
    expect(f.repairScope.value).toBe('mine')
    expect(mocks.myRepair).toHaveBeenCalledOnce()
  })
  it('opens allowed one-off assistant actions after removing the URL action', async () => {
    const f = mount('reservation', 'student')
    await flush()
    const open = vi.fn()
    f.workflow.value = { open }
    mocks.route.query.action = 'apply'
    await flush()
    expect(mocks.replace).toHaveBeenCalledWith({ path: '/equipment', query: {} })
    expect(open).toHaveBeenCalledWith('apply')
  })
  it('does not open an assistant student-only apply action for an admin', async () => {
    const f = mount('reservation')
    await flush()
    const open = vi.fn()
    f.workflow.value = { open }
    mocks.route.query.action = 'apply'
    await flush()
    expect(open).not.toHaveBeenCalled()
  })
  it('removes timer and resize listener on unmount', async () => {
    const f = mount()
    await flush()
    expect(vi.getTimerCount()).toBe(1)
    mounted.unmount()
    mounted = null
    window.innerWidth = 400
    window.dispatchEvent(new Event('resize'))
    expect(f.compact.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })
})
