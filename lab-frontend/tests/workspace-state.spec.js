import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { reactive, nextTick } from 'vue'
import { mountComposable, deferred } from './helpers/composable'
const state = vi.hoisted(() => ({ token: 'session', user: null }))
vi.mock('@/stores/user', () => ({ useUserStore: () => state.user }))
vi.mock('@/utils/auth', () => ({ getToken: () => state.token }))
vi.mock('@/api/workspace', () => ({ getWorkspace: vi.fn() }))
vi.mock('@/api/repair', () => ({ getRepairPage: vi.fn() }))
import { getWorkspace } from '@/api/workspace'
import { getRepairPage } from '@/api/repair'
import { useWorkspace } from '@/composables/useWorkspace'
let mounted
async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
beforeEach(() => {
  state.token = 'session'
  state.user = reactive({ token: 'session', role: 'lab_admin' })
  getWorkspace
    .mockReset()
    .mockResolvedValue({ data: { repairs: { records: [], total: 0 } }, errors: {} })
  getRepairPage.mockReset().mockResolvedValue({ records: [{ id: 2, status: 1 }], total: 1 })
})
afterEach(() => mounted?.unmount())
it('changes repair state through one scoped page request, without reloading overview or reservations', async () => {
  mounted = mountComposable(useWorkspace)
  await flush()
  await mounted.result.changeRepairStatus(1)
  expect(getWorkspace).toHaveBeenCalledTimes(1)
  expect(getRepairPage).toHaveBeenCalledExactlyOnceWith({ current: 1, size: 5, status: 1 })
  expect(mounted.result.data.value.repairs.total).toBe(1)
})
it('initial dashboard result cannot overwrite a newer repair tab', async () => {
  const first = deferred()
  getWorkspace.mockReturnValue(first.promise)
  mounted = mountComposable(useWorkspace)
  await mounted.result.changeRepairStatus(1)
  first.resolve({
    data: { overview: { equipmentCount: 20 }, repairs: { records: [], total: 8 } },
    errors: {},
  })
  await flush()
  expect(mounted.result.data.value.repairs.records[0].status).toBe(1)
  expect(mounted.result.data.value.overview.equipmentCount).toBe(20)
})
it('failed repair tab shows error, never a false zero; latest request wins', async () => {
  mounted = mountComposable(useWorkspace)
  await flush()
  const old = deferred()
  getRepairPage.mockReturnValueOnce(old.promise).mockRejectedValueOnce(new Error('连接失败'))
  const previous = mounted.result.changeRepairStatus(1)
  await mounted.result.changeRepairStatus(2)
  old.resolve({ records: [{ id: 1 }], total: 10 })
  await previous
  expect(mounted.result.errors.value.repairs).toBe('连接失败')
  expect(mounted.result.data.value.repairs).toBeUndefined()
  expect(mounted.result.repairStatus.value).toBe(2)
})
it('clears account data on logout and ignores in-flight repair requests', async () => {
  mounted = mountComposable(useWorkspace)
  await flush()
  const old = deferred()
  getRepairPage.mockReturnValue(old.promise)
  const request = mounted.result.changeRepairStatus(1)
  state.token = ''
  state.user.token = ''
  await flush()
  old.resolve({ records: [{ id: 1 }], total: 10 })
  await request
  expect(mounted.result.data.value).toEqual({})
})
