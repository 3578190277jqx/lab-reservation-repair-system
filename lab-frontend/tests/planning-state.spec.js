import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { reactive, nextTick } from 'vue'
import { mountComposable, deferred } from './helpers/composable'
const state = vi.hoisted(() => ({ token: 'session', user: null }))
vi.mock('@/stores/user', () => ({ useUserStore: () => state.user }))
vi.mock('@/utils/auth', () => ({ getToken: () => state.token }))
vi.mock('vue-router', () => ({ useRoute: () => ({ fullPath: '/equipment' }) }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), success: vi.fn() } }))
vi.mock('@/api/reservation', () => ({
  getReservationSchedule: vi.fn(),
  applyReservation: vi.fn(),
  auditReservation: vi.fn(),
  cancelReservation: vi.fn(),
  signInReservation: vi.fn(),
}))
import { getReservationSchedule, applyReservation } from '@/api/reservation'
import { useReservationSchedule } from '@/composables/useReservationSchedule'
import { useWorkflowDialog } from '@/composables/useWorkflowDialog'
const mounted = []
function mount(fn) {
  const m = mountComposable(fn)
  mounted.push(m)
  return m.result
}
async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
beforeEach(() => {
  state.token = 'session'
  state.user = reactive({ token: 'session', role: 'student', user: { id: 3, role: 'student' } })
  getReservationSchedule
    .mockReset()
    .mockResolvedValue({ bookable: true, slots: [], truncated: false })
  applyReservation.mockReset()
})
afterEach(() => {
  mounted.splice(0).forEach((m) => m.unmount())
  vi.useRealTimers()
})
describe('selected equipment schedule', () => {
  it('does not query until selected; switching date only queries the selected device', async () => {
    const input = reactive({ equipmentId: null })
    const s = mount(() => useReservationSchedule(input))
    expect(getReservationSchedule).not.toHaveBeenCalled()
    input.equipmentId = 12
    await flush()
    expect(getReservationSchedule).toHaveBeenCalledTimes(1)
    s.date.value = '2026-11-01'
    await flush()
    expect(getReservationSchedule).toHaveBeenLastCalledWith({ equipmentId: 12, date: '2026-11-01' })
    expect(getReservationSchedule).toHaveBeenCalledTimes(2)
  })
  it('ignores old equipment responses and clears on logout', async () => {
    const slow = deferred()
    getReservationSchedule.mockReturnValueOnce(slow.promise)
    const input = reactive({ equipmentId: 1 })
    const s = mount(() => useReservationSchedule(input))
    input.equipmentId = 2
    await flush()
    slow.resolve({ slots: [{ startTime: 'old' }] })
    await flush()
    expect(s.schedule.value.slots).toEqual([])
    state.token = ''
    state.user.token = ''
    await flush()
    expect(s.schedule.value).toBeNull()
  })
  it('detects half-open overlap; preserves cross-day timestamps and truncation', async () => {
    const slot = { startTime: '2026-11-01 23:00:00', endTime: '2026-11-02 01:00:00', status: 0 }
    getReservationSchedule.mockResolvedValue({ bookable: true, slots: [slot], truncated: true })
    const input = reactive({
      equipmentId: 1,
      startTime: '2026-11-02 01:00:00',
      endTime: '2026-11-02 02:00:00',
    })
    const s = mount(() => useReservationSchedule(input))
    await flush()
    expect(s.conflict.value).toBe(false)
    input.startTime = '2026-11-02 00:30:00'
    await flush()
    expect(s.conflict.value).toBe(true)
    expect(s.schedule.value.slots[0]).toEqual(slot)
    expect(s.schedule.value.truncated).toBe(true)
  })
})
describe('shared booking draft', () => {
  it('keeps the form open during submission or upload and preserves the normal close callback', () => {
    const w = mount(() => useWorkflowDialog())
    w.open('apply', { id: 12, status: 0 })
    w.form.purpose = '实验用途'
    const done = vi.fn()
    w.busy.value = true
    w.close(done)
    w.busy.value = false
    w.uploading.value = true
    w.close(done)
    expect(w.visible.value).toBe(true)
    expect(w.form.purpose).toBe('实验用途')
    expect(done).not.toHaveBeenCalled()
    w.uploading.value = false
    w.close(done)
    expect(w.visible.value).toBe(false)
    expect(done).toHaveBeenCalledTimes(1)
  })
  it('preserves input after rejection, refreshes schedule and blocks duplicate submission', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-29T00:00:00Z'))
    const pending = deferred()
    applyReservation.mockReturnValue(pending.promise)
    const onSuccess = vi.fn(),
      w = mount(() => useWorkflowDialog({ onSuccess }))
    w.open('apply', { id: 12, status: 1, name: '示波器' })
    Object.assign(w.form, {
      times: ['2026-11-01 09:00:00', '2026-11-02 10:00:00'],
      purpose: '光学实验',
    })
    expect(w.dirty.value).toBe(true)
    const request = w.submit()
    await w.submit()
    expect(applyReservation).toHaveBeenCalledTimes(1)
    expect(applyReservation).toHaveBeenCalledWith({
      equipmentId: 12,
      startTime: '2026-11-01 09:00:00',
      endTime: '2026-11-02 10:00:00',
      purpose: '光学实验',
    })
    pending.reject(new Error('时段已被占用'))
    await request
    expect(w.visible.value).toBe(true)
    expect(w.form.purpose).toBe('光学实验')
    expect(w.form.times).toHaveLength(2)
    expect(w.error.value).toBe('时段已被占用')
    expect(w.scheduleRevision.value).toBe(1)
    expect(onSuccess).not.toHaveBeenCalled()
  })
  it('does not apply an old response after session changes', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-29T00:00:00Z'))
    const pending = deferred()
    applyReservation.mockReturnValue(pending.promise)
    const onSuccess = vi.fn(),
      w = mount(() => useWorkflowDialog({ onSuccess }))
    w.open('apply', { id: 1, status: 0 })
    w.chooseTomorrow()
    w.form.purpose = '实验'
    const request = w.submit()
    state.token = 'new'
    state.user.token = 'new'
    await flush()
    pending.resolve({})
    await request
    expect(w.visible.value).toBe(false)
    expect(onSuccess).not.toHaveBeenCalled()
  })
})
