// 页面只控制操作入口；身份、管理范围、状态和冲突仍由后端再次验证。
export const repairStatusNames = ['待受理', '已受理', '维修中', '已完成']
export const actionNames = {
  apply: '申请预约', submit: '提交报修', audit: '审核预约', cancel: '取消预约',
  signIn: '预约签到', accept: '受理工单', start: '开始维修', complete: '完成维修', records: '工单详情',
}
export function beijingTime(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}$/.test(value)) return NaN
  return Date.parse(value.replace(' ', 'T') + '+08:00')
}
export function reservationTimeError(times, now = Date.now()) {
  if (!Array.isArray(times) || times.length !== 2 || times.some(value => !value)) return '请选择预约开始时间和结束时间（北京时间）'
  const [start, end] = times.map(beijingTime)
  if (!Number.isFinite(start) || !Number.isFinite(end)) return '预约时间格式无效，请重新选择'
  if (start <= now) {
    const current = new Date(now + 8 * 3600000).toISOString().slice(0, 16).replace('T', ' ')
    return `开始时间已过去，请选择未来时段（当前北京时间：${current}）`
  }
  return end <= start ? '结束时间必须晚于开始时间' : ''
}
export function isPastReservationDate(date, now = Date.now()) {
  const day = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return day < new Date(now + 8 * 3600000).toISOString().slice(0, 10)
}
export function tomorrowReservationRange(now = Date.now()) {
  const day = new Date(now + 32 * 3600000).toISOString().slice(0, 10)
  return [`${day} 09:00:00`, `${day} 10:00:00`]
}
export function rowActions(resource, row, profile, now = Date.now()) {
  const role = profile?.role
  const manager = ['admin', 'lab_admin'].includes(role)
  if (resource === 'equipment') {
    const actions = []
    if (role === 'student' && [0, 1].includes(row.status)) actions.push('apply')
    if (['admin', 'lab_admin', 'student'].includes(role) && row.status !== 3) actions.push('submit')
    return actions
  }
  if (resource === 'reservation') {
    if (manager) return row.status === 0 ? ['audit'] : []
    if (role !== 'student' || String(row.studentId) !== String(profile.id)) return []
    const start = beijingTime(row.startTime), end = beijingTime(row.endTime)
    const actions = []
    if ([0, 1].includes(row.status) && !row.signInTime && now < start) actions.push('cancel')
    if (row.status === 1 && !row.signInTime && now >= start && now < start + 30 * 60000 && now < end) actions.push('signIn')
    return actions
  }
  if (resource === 'repair') {
    const actions = ['records']
    if (manager && row.status === 0) actions.push('accept')
    const handler = role === 'admin' || String(row.handlerId) === String(profile?.id)
    if (manager && handler && row.status === 1) actions.push('start')
    if (manager && handler && row.status === 2) actions.push('complete')
    return actions
  }
  return []
}
function text(value, label, max, required = true) {
  const result = String(value ?? '').trim()
  if (required && !result) throw new Error(`请填写${label}`)
  if (result.length > max) throw new Error(`${label}不能超过${max}个字符`)
  return result
}
function positiveId(value) {
  if (!/^[1-9]\d*$/.test(String(value ?? ''))) throw new Error('请选择设备或有效记录')
  return value
}
export function actionPayload(action, form, row, now = Date.now()) {
  if (action === 'apply') {
    const [startTime, endTime] = form.times || []
    const timeError = reservationTimeError(form.times, now)
    if (timeError) throw new Error(timeError)
    return { equipmentId: positiveId(form.equipmentId), startTime, endTime, purpose: text(form.purpose, '预约用途', 500) }
  }
  if (action === 'submit') return {
    equipmentId: positiveId(form.equipmentId), description: text(form.description, '故障描述', 2000),
    imageUrl: form.imageUrl || null, analyzeImage: !!form.imageUrl && !!form.analyzeImage,
  }
  if (action === 'audit') {
    if (typeof form.approved !== 'boolean') throw new Error('请选择审核结果')
    return { reservationId: positiveId(row.id), approved: form.approved, remark: text(form.remark, '审核意见', 500, !form.approved) }
  }
  if (action === 'cancel') return { reservationId: positiveId(row.id), reason: text(form.reason, '取消原因', 500, false) }
  if (action === 'signIn') return { reservationId: positiveId(row.id) }
  if (['accept', 'start', 'complete'].includes(action)) {
    const payload = { repairOrderId: positiveId(row.id), content: text(form.content, action === 'complete' ? '维修结果' : '操作说明', 2000, action === 'complete') }
    if (action === 'complete') {
      const cost = String(form.repairCost ?? '')
      if (!/^\d{1,8}(\.\d{1,2})?$/.test(cost)) throw new Error('维修费用须为非负金额，最多8位整数、2位小数')
      payload.repairCost = cost
    }
    return payload
  }
  throw new Error('此操作不可用')
}
export function validateImage(file) {
  if (!file || !['image/jpeg', 'image/png'].includes(file.type)) throw new Error('仅支持 JPEG 或 PNG 图片')
  if (file.size <= 0 || file.size > 5 * 1024 * 1024) throw new Error('图片须大于0字节且不超过5MiB')
}
