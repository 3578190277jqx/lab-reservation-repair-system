import request from '@/utils/request'

/** ApplyRequest: { equipmentId, startTime, endTime, purpose }；时间格式 yyyy-MM-dd HH:mm:ss。 */
export function applyReservation(data) {
  return request({ url: '/reservation/apply', method: 'post', data })
}

/** ReservationPageQuery: { current?, size?, equipmentId?, labId?, status? }；当前用户由令牌确定。 */
export function getMyReservations(params) {
  return request({ url: '/reservation/my', method: 'get', params })
}

/** ReservationPageQuery: { current?, size?, equipmentId?, studentId?, labId?, status? }。 */
export function getReservationPage(params) {
  return request({ url: '/reservation/page', method: 'get', params })
}

/** AuditRequest: { reservationId, approved, remark? }；approved=false 时 remark 必填。 */
export function auditReservation(data) {
  return request({ url: '/reservation/audit', method: 'post', data })
}

/** CancelReservationRequest: { reservationId, reason? }。 */
export function cancelReservation(data) {
  return request({ url: '/reservation/cancel', method: 'post', data })
}

/** ReservationIdRequest: { reservationId }；实际签到时间由服务端生成。 */
export function signInReservation(data) {
  return request({ url: '/reservation/sign-in', method: 'post', data })
}

/** 匿名单日排期快照；不返回其他学生的身份和用途。 */
export function getReservationSchedule(params) {
  return request({ url: '/reservation/schedule', method: 'get', params, silent: true })
}

export const getReservationDetail = id => request({ url: `/reservation/${encodeURIComponent(id)}`, method: 'get' })
