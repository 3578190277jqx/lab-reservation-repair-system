import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { reactive, ref, nextTick } from 'vue'
import { deferred, mountComposable } from './helpers/composable'

const session = vi.hoisted(() => ({ token: 'first' }))
vi.mock('@/utils/auth', () => ({ getToken: () => session.token }))
vi.mock('@/api/notification', () => ({
  getMyNotifications: vi.fn(),
  getContactTodos: vi.fn(),
  confirmNotification: vi.fn(),
  markContacted: vi.fn(),
}))
import {
  confirmNotification,
  getContactTodos,
  getMyNotifications,
  markContacted,
} from '@/api/notification'
import { useNotificationCenter } from '@/composables/useNotificationCenter'

let mounted
async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
beforeEach(() => {
  session.token = 'first'
  getMyNotifications.mockResolvedValue({ records: [{ id: 1 }], total: 1 })
  getContactTodos.mockResolvedValue({ records: [{ id: 2 }], total: 1 })
  confirmNotification.mockResolvedValue(null)
  markContacted.mockResolvedValue(null)
})
afterEach(() => mounted?.unmount())

it('student confirmation calls the student action and refreshes the list', async () => {
  const user = reactive({ token: 'first', role: 'student' })
  mounted = mountComposable(() => useNotificationCenter(ref('student'), user))
  await flush()
  await mounted.result.act(1)
  expect(confirmNotification).toHaveBeenCalledExactlyOnceWith(1)
  expect(getMyNotifications).toHaveBeenCalledTimes(2)
  expect(markContacted).not.toHaveBeenCalled()
})

it('manager contact is distinct from student confirmation and ignores duplicate clicks', async () => {
  const user = reactive({ token: 'first', role: 'lab_admin' })
  const pending = deferred()
  markContacted.mockReturnValue(pending.promise)
  mounted = mountComposable(() => useNotificationCenter(ref('manager'), user))
  await flush()
  const first = mounted.result.act(2)
  await mounted.result.act(2)
  expect(markContacted).toHaveBeenCalledExactlyOnceWith(2)
  pending.resolve()
  await first
  expect(confirmNotification).not.toHaveBeenCalled()
  expect(getContactTodos).toHaveBeenCalledTimes(2)
})

it('does not expose an old account response after the account changes', async () => {
  const old = deferred()
  getMyNotifications
    .mockReturnValueOnce(old.promise)
    .mockResolvedValueOnce({ records: [], total: 0 })
  const user = reactive({ token: 'first', role: 'student' })
  mounted = mountComposable(() => useNotificationCenter(ref('student'), user))
  session.token = 'second'
  user.token = 'second'
  await flush()
  old.resolve({ records: [{ id: 99 }], total: 1 })
  await flush()
  expect(mounted.result.state.records).toEqual([])
})

it('returns to the last valid page after the last contact todo disappears', async () => {
  const user = reactive({ token: 'first', role: 'lab_admin' })
  getContactTodos
    .mockResolvedValueOnce({ records: [{ id: 1 }], total: 21 })
    .mockResolvedValueOnce({ records: [{ id: 2 }], total: 21 })
    .mockResolvedValueOnce({ records: [], total: 20 })
    .mockResolvedValueOnce({ records: [{ id: 1 }], total: 20 })
  mounted = mountComposable(() => useNotificationCenter(ref('manager'), user))
  await flush()
  await mounted.result.load(2)
  await mounted.result.act(2)
  expect(getContactTodos).toHaveBeenCalledTimes(4)
  expect(getContactTodos).toHaveBeenLastCalledWith({ current: 1, size: 20 })
  expect(mounted.result.state.current).toBe(1)
  expect(mounted.result.state.records).toEqual([{ id: 1 }])
})

it('does not change page after a student confirms a notification that remains in the list', async () => {
  const user = reactive({ token: 'first', role: 'student' })
  getMyNotifications
    .mockResolvedValueOnce({ records: [{ id: 1 }], total: 21 })
    .mockResolvedValueOnce({ records: [{ id: 2 }], total: 21 })
    .mockResolvedValueOnce({ records: [{ id: 2, confirmedAt: '2030-01-01' }], total: 21 })
  mounted = mountComposable(() => useNotificationCenter(ref('student'), user))
  await flush()
  await mounted.result.load(2)
  await mounted.result.act(2)
  expect(getMyNotifications).toHaveBeenCalledTimes(3)
  expect(getMyNotifications).toHaveBeenLastCalledWith({ current: 2, size: 20 })
  expect(mounted.result.state.current).toBe(2)
  expect(mounted.result.state.records[0].confirmedAt).toBe('2030-01-01')
})

it('returns to the first page once when no contact todos remain', async () => {
  const user = reactive({ token: 'first', role: 'lab_admin' })
  getContactTodos
    .mockResolvedValueOnce({ records: [{ id: 1 }], total: 21 })
    .mockResolvedValueOnce({ records: [{ id: 2 }], total: 21 })
    .mockResolvedValueOnce({ records: [], total: 0 })
    .mockResolvedValueOnce({ records: [], total: 0 })
  mounted = mountComposable(() => useNotificationCenter(ref('manager'), user))
  await flush()
  await mounted.result.load(2)
  await mounted.result.act(2)
  expect(getContactTodos).toHaveBeenCalledTimes(4)
  expect(mounted.result.state.current).toBe(1)
  expect(mounted.result.state.records).toEqual([])
})

it('ignores a previous account response when a fallback page request is in flight', async () => {
  const user = reactive({ token: 'first', role: 'lab_admin' })
  const oldPage = deferred()
  getContactTodos
    .mockResolvedValueOnce({ records: [{ id: 1 }], total: 21 })
    .mockResolvedValueOnce({ records: [{ id: 2 }], total: 21 })
    .mockResolvedValueOnce({ records: [], total: 20 })
    .mockReturnValueOnce(oldPage.promise)
    .mockResolvedValueOnce({ records: [{ id: 88 }], total: 1 })
  mounted = mountComposable(() => useNotificationCenter(ref('manager'), user))
  await flush()
  await mounted.result.load(2)
  const previousAction = mounted.result.act(2)
  await flush()
  session.token = 'second'
  user.token = 'second'
  await flush()
  oldPage.resolve({ records: [{ id: 99 }], total: 20 })
  await previousAction
  expect(mounted.result.state.records).toEqual([{ id: 88 }])
  expect(mounted.result.state.current).toBe(1)
  expect(getContactTodos).toHaveBeenCalledTimes(5)
})

it('stops retrying if the total keeps shrinking during page fallback', async () => {
  const user = reactive({ token: 'first', role: 'lab_admin' })
  getContactTodos
    .mockResolvedValueOnce({ records: [{ id: 1 }], total: 181 })
    .mockResolvedValueOnce({ records: [{ id: 2 }], total: 181 })
    .mockResolvedValueOnce({ records: [], total: 160 })
    .mockResolvedValueOnce({ records: [], total: 140 })
    .mockResolvedValueOnce({ records: [], total: 120 })
    .mockResolvedValueOnce({ records: [], total: 100 })
  mounted = mountComposable(() => useNotificationCenter(ref('manager'), user))
  await flush()
  await mounted.result.load(10)
  await mounted.result.act(2)
  expect(getContactTodos).toHaveBeenCalledTimes(6)
  expect(mounted.result.state.error).toBe('通知列表变化频繁，请重新加载')
  expect(mounted.result.state.loading).toBe(false)
})
