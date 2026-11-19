import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, shallowRef } from 'vue'
import { deferred, mountComposable } from './helpers/composable'
const mocks = vi.hoisted(() => ({ page: vi.fn(), token: 'admin-token' }))
vi.mock('@/api/operation-log', () => ({ getOperationLogPage: mocks.page }))
vi.mock('@/utils/auth', () => ({ getToken: () => mocks.token }))
import { useOperationLogs } from '@/composables/useOperationLogs'
let mounted
beforeEach(() => {
  vi.clearAllMocks()
  mocks.token = 'admin-token'
  mocks.page.mockResolvedValue({ records: [{ id: 1, module: 'equipment', success: 1 }], total: 1 })
})
afterEach(() => {
  mounted?.unmount()
  mounted = null
})
function mount(role = shallowRef('admin')) {
  mounted = mountComposable(() => useOperationLogs(role))
  return mounted.result
}
async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
describe('admin operation log list', () => {
  it.each(['student', 'lab_admin', 'guest'])('never requests audit data for %s', async (role) => {
    const f = mount(role)
    await f.load()
    expect(mocks.page).not.toHaveBeenCalled()
    expect(f.rows.value).toEqual([])
  })
  it('sends only supported filters and resets to page 1 on search', async () => {
    const f = mount()
    await flush()
    f.page.current = 3
    f.filters.module = ' equipment '
    f.filters.success = 0
    await f.search()
    expect(mocks.page).toHaveBeenLastCalledWith({
      module: 'equipment',
      success: 0,
      current: 1,
      size: 20,
    })
    await f.changeSize(50)
    expect(mocks.page).toHaveBeenLastCalledWith({
      module: 'equipment',
      success: 0,
      current: 1,
      size: 50,
    })
    await f.reset()
    expect(mocks.page).toHaveBeenLastCalledWith({ current: 1, size: 50 })
  })
  it('clears audit records immediately when admin role is removed', async () => {
    const role = shallowRef('admin'),
      f = mount(role)
    await flush()
    expect(f.rows.value.length).toBe(1)
    role.value = 'lab_admin'
    await flush()
    expect(f.rows.value).toEqual([])
    expect(f.total.value).toBe(0)
  })
  it.each([undefined, null])('supports clearing the module selector to %s', async (value) => {
    const f = mount()
    await flush()
    f.filters.module = value
    await f.search()
    expect(mocks.page).toHaveBeenLastCalledWith({ current: 1, size: 20 })
  })
  it('ignores an older response after a session change', async () => {
    const delivery = deferred()
    mocks.page.mockReturnValue(delivery.promise)
    const f = mount()
    mocks.token = 'new-token'
    delivery.resolve({ records: [{ id: 5 }], total: 1 })
    await flush()
    expect(f.rows.value).toEqual([])
  })
  it('shows a retryable error for malformed data and recovers on retry', async () => {
    mocks.page.mockResolvedValueOnce({ total: 1 })
    const f = mount()
    await flush()
    expect(f.error.value).toContain('格式异常')
    await f.load()
    expect(f.error.value).toBe('')
    expect(f.rows.value.length).toBe(1)
  })
})
