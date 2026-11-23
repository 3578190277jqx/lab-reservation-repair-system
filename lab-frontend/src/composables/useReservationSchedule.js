import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getReservationSchedule } from '@/api/reservation'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import { beijingTime } from '@/utils/workflow'

/** 一台设备、一个自然日的占用快照；不预取列表中其他设备的排期。 */
export function useReservationSchedule(input) {
  const user = useUserStore()
  const date = ref(new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10))
  const schedule = ref(null),
    loading = ref(false),
    error = ref('')
  let sequence = 0
  watch(
    () => input.startTime,
    (value) => {
      if (value) date.value = value.slice(0, 10)
    },
    { immediate: true },
  )
  const conflict = computed(() => {
    const start = beijingTime(input.startTime),
      end = beijingTime(input.endTime)
    return !!schedule.value?.slots.some(
      (slot) => start < beijingTime(slot.endTime) && end > beijingTime(slot.startTime),
    )
  })
  async function load() {
    const id = ++sequence,
      token = getToken(),
      role = user.role
    schedule.value = null
    error.value = ''
    loading.value = false
    if (!input.equipmentId || !date.value || !token) return
    loading.value = true
    try {
      const data = await getReservationSchedule({
        equipmentId: input.equipmentId,
        date: date.value,
      })
      if (id !== sequence || token !== getToken() || role !== user.role) return
      if (!data || !Array.isArray(data.slots)) throw new Error('占用时段返回格式错误')
      schedule.value = data
    } catch (e) {
      if (id === sequence && token === getToken() && role === user.role)
        error.value = e.message || '暂时无法加载占用时段'
    } finally {
      if (id === sequence) loading.value = false
    }
  }
  watch(
    [() => input.equipmentId, date, () => input.refreshKey, () => user.token, () => user.role],
    load,
    { immediate: true },
  )
  onBeforeUnmount(() => {
    sequence++
  })
  return { date, schedule, loading, error, conflict, load }
}
