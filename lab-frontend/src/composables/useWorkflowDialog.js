import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'
import {
  actionNames,
  actionPayload,
  isPastReservationDate,
  reservationTimeError,
  tomorrowReservationRange,
  repairStatusNames,
  rowActions,
  validateImage,
} from '@/utils/workflow'
import {
  applyReservation,
  auditReservation,
  cancelReservation,
  signInReservation,
} from '@/api/reservation'
import {
  submitRepair,
  acceptRepair,
  startRepair,
  completeRepair,
  getRepairRecords,
} from '@/api/repair'
import { uploadImage, getImageBlob } from '@/api/file'

export function useWorkflowDialog({ onSuccess = () => {} } = {}) {
  const route = useRoute(),
    user = useUserStore()
  const visible = ref(false),
    action = ref(''),
    row = ref({}),
    busy = ref(false),
    uploading = ref(false)
  const error = ref(''),
    preview = ref(''),
    fileName = ref(''),
    records = ref([]),
    detailsLoading = ref(false)
  const imageError = ref(''),
    recordError = ref('')
  const form = reactive({
    equipmentId: '',
    times: [],
    purpose: '',
    description: '',
    imageUrl: '',
    analyzeImage: false,
    approved: true,
    remark: '',
    reason: '',
    content: '',
    repairCost: '0',
  })
  const scheduleRevision = ref(0)
  const reservationNow = ref(Date.now()),
    timeAttempted = ref(false)
  const defaultReservationTimes = [new Date(2000, 0, 1, 9), new Date(2000, 0, 1, 10)]
  const timeIssue = computed(() =>
    timeAttempted.value || form.times?.length
      ? reservationTimeError(form.times, reservationNow.value)
      : '',
  )
  function reservationTimeChanged() {
    reservationNow.value = Date.now()
    timeAttempted.value = false
    error.value = ''
  }
  function chooseTomorrow() {
    form.times = tomorrowReservationRange()
    reservationTimeChanged()
  }
  function disablePastDate(date) {
    return isPastReservationDate(date)
  }
  let version = 0,
    openingToken = ''
  const title = computed(() => actionNames[action.value] || '业务操作')
  const lockedEquipment = computed(
    () => ['apply', 'submit'].includes(action.value) && !!row.value.id,
  )
  const isCreate = computed(() => ['apply', 'submit'].includes(action.value))
  const api = {
    apply: applyReservation,
    submit: submitRepair,
    audit: auditReservation,
    cancel: cancelReservation,
    signIn: signInReservation,
    accept: acceptRepair,
    start: startRepair,
    complete: completeRepair,
  }
  function revokePreview() {
    if (preview.value) URL.revokeObjectURL(preview.value)
    preview.value = ''
  }
  function stillCurrent(id = version) {
    return visible.value && id === version && !!openingToken && openingToken === getToken()
  }
  function reset() {
    version++
    visible.value = false
    busy.value = false
    uploading.value = false
    detailsLoading.value = false
    revokePreview()
  }
  function open(kind, item = {}) {
    if (busy.value || uploading.value) return
    // 把弹窗与打开时的会话绑定，不能用新账号提交旧账号已打开的操作。
    const token = getToken()
    if (!token) {
      ElMessage.warning('请先登录')
      return
    }
    if (kind === 'apply' && user.role !== 'student') {
      ElMessage.warning('请使用学生账号申请预约')
      return
    }
    if (item.id) {
      const resource = ['apply', 'submit'].includes(kind)
        ? 'equipment'
        : ['audit', 'cancel', 'signIn'].includes(kind)
          ? 'reservation'
          : 'repair'
      if (!rowActions(resource, item, user.user).includes(kind)) {
        ElMessage.warning('此记录当前不能执行该操作，请刷新列表')
        return
      }
    }
    reset()
    openingToken = token
    action.value = kind
    row.value = { ...item }
    Object.assign(form, {
      equipmentId: item.id || '',
      times: [],
      purpose: '',
      description: '',
      imageUrl: '',
      analyzeImage: false,
      approved: true,
      remark: '',
      reason: '',
      content: '',
      repairCost: '0',
    })
    error.value = ''
    fileName.value = ''
    imageError.value = ''
    recordError.value = ''
    records.value = []
    visible.value = true
    reservationTimeChanged()
    if (kind === 'records') loadDetails()
  }
  function close(done) {
    if (!busy.value && !uploading.value) {
      reset()
      done?.()
    }
  }
  async function loadDetails() {
    if (!stillCurrent()) return
    const id = version
    detailsLoading.value = true
    recordError.value = ''
    imageError.value = ''
    await Promise.all([
      getRepairRecords(row.value.id)
        .then((data) => {
          if (stillCurrent(id)) records.value = data
        })
        .catch((e) => {
          if (stillCurrent(id)) recordError.value = e.message
        }),
      row.value.imageUrl
        ? getImageBlob(row.value.imageUrl, { silent: true })
            .then((blob) => {
              if (stillCurrent(id)) {
                revokePreview()
                preview.value = URL.createObjectURL(blob)
              }
            })
            .catch((e) => {
              if (stillCurrent(id)) imageError.value = e.message
            })
        : Promise.resolve(),
    ])
    if (stillCurrent(id)) detailsLoading.value = false
  }
  async function chooseImage(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || uploading.value || busy.value || !stillCurrent()) return
    const id = version
    error.value = ''
    try {
      validateImage(file)
      uploading.value = true
      const uploaded = await uploadImage(file)
      if (!stillCurrent(id)) return
      revokePreview()
      preview.value = URL.createObjectURL(file)
      form.imageUrl = uploaded.imageUrl
      fileName.value = file.name
    } catch (e) {
      if (stillCurrent(id)) error.value = e.message
    } finally {
      if (id === version) uploading.value = false
    }
  }
  function removeImage() {
    if (!busy.value && !uploading.value) {
      revokePreview()
      form.imageUrl = ''
      form.analyzeImage = false
      fileName.value = ''
    }
  }
  async function submit() {
    if (busy.value || uploading.value) return
    if (!stillCurrent()) {
      reset()
      ElMessage.warning('登录状态已变化，请重新打开操作')
      return
    }
    const id = version,
      kind = action.value
    error.value = ''
    if (kind === 'apply') {
      reservationNow.value = Date.now()
      timeAttempted.value = true
      if (timeIssue.value) return
    }
    try {
      const payload = actionPayload(kind, form, row.value)
      // 用户在填表期间时间或记录状态可能变化，提交前再次校验客户端可见状态。
      if (row.value.id) {
        const resource = isCreate.value
          ? 'equipment'
          : ['audit', 'cancel', 'signIn'].includes(kind)
            ? 'reservation'
            : 'repair'
        if (!rowActions(resource, row.value, user.user).includes(kind))
          throw new Error('此记录当前不能执行该操作，请刷新后重试')
      }
      busy.value = true
      const result = await api[kind](payload)
      if (!stillCurrent(id)) return
      reset()
      ElMessage.success(
        kind === 'apply'
          ? '预约申请已提交，等待管理员审核'
          : kind === 'submit'
            ? '报修已提交，等待管理员受理'
            : '操作成功',
      )
      onSuccess({ action: kind, result })
    } catch (e) {
      if (stillCurrent(id)) {
        error.value = e.message || '提交失败，请重试'
        if (kind === 'apply') scheduleRevision.value++
      }
    } finally {
      if (id === version) busy.value = false
    }
  }
  watch([() => route.fullPath, () => user.token, () => user.role], reset)
  onBeforeUnmount(reset)
  const dirty = computed(
    () =>
      visible.value && action.value === 'apply' && (!!form.purpose || form.times?.some(Boolean)),
  )
  return {
    visible,
    action,
    row,
    busy,
    uploading,
    error,
    preview,
    fileName,
    records,
    detailsLoading,
    imageError,
    recordError,
    form,
    scheduleRevision,
    defaultReservationTimes,
    timeIssue,
    reservationTimeChanged,
    chooseTomorrow,
    disablePastDate,
    title,
    lockedEquipment,
    isCreate,
    open,
    close,
    reset,
    loadDetails,
    chooseImage,
    removeImage,
    submit,
    dirty,
    repairStatusNames,
  }
}
