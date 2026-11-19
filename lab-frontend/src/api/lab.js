import request from '@/utils/request'

/** LabPageQuery: { current?, size?, labCode?, name?, status?, adminId? }。 */
export function getLabPage(params) {
  return request({ url: '/lab/page', method: 'get', params })
}

export function getLab(id) {
  return request({ url: `/lab/${encodeURIComponent(id)}`, method: 'get' })
}

/** LabSaveRequest: { labCode, name, location, status, adminId?, contactPhone?, description? }。 */
export function createLab(data) {
  return request({ url: '/lab', method: 'post', data })
}

/** 完整更新；data 字段与 LabSaveRequest 相同，主键通过路径传递。 */
export function updateLab(id, data) {
  return request({ url: `/lab/${encodeURIComponent(id)}`, method: 'put', data })
}

export function deleteLab(id) {
  return request({ url: `/lab/${encodeURIComponent(id)}`, method: 'delete' })
}
