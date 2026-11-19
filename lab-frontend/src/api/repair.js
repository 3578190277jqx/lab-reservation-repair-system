import request from '@/utils/request'

/** RepairSubmitRequest: { equipmentId, description, imageUrl?, analyzeImage? }；图片先通过 uploadImage 上传。 */
export function submitRepair(data) {
  return request({ url: '/repair/submit', method: 'post', data, timeout: 60000 })
}

/** RepairPageQuery: { current?, size?, equipmentId?, labId?, status? }；仅返回当前用户工单。 */
export function getMyRepairs(params) {
  return request({ url: '/repair/my', method: 'get', params })
}

/** RepairPageQuery: { current?, size?, equipmentId?, reporterId?, labId?, status? }。 */
export function getRepairPage(params) {
  return request({ url: '/repair/page', method: 'get', params })
}

/** RepairActionRequest: { repairOrderId, content? }。 */
export function acceptRepair(data) {
  return request({ url: '/repair/accept', method: 'post', data })
}

/** RepairActionRequest: { repairOrderId, content? }。 */
export function startRepair(data) {
  return request({ url: '/repair/start', method: 'post', data })
}

/** RepairCompleteRequest: { repairOrderId, content, repairCost }；费用非负，最多两位小数。 */
export function completeRepair(data) {
  return request({ url: '/repair/complete', method: 'post', data })
}

export function getRepairRecords(id) {
  return request({ url: `/repair/${encodeURIComponent(id)}/records`, method: 'get' })
}
