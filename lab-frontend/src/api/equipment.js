import request from '@/utils/request'

/** EquipmentPageQuery: { current?, size?, labId?, equipmentCode?, name?, model?, status? }。 */
export function getEquipmentPage(params) {
  return request({ url: '/equipment/page', method: 'get', params })
}

export function getEquipment(id) {
  return request({ url: `/equipment/${encodeURIComponent(id)}`, method: 'get' })
}

/**
 * EquipmentSaveRequest: { labId, equipmentCode, name, status,
 * model?, manufacturer?, purchaseDate?, imageUrl?, description? }。
 * purchaseDate 格式为 yyyy-MM-dd；status 为 0可用、1已预约、2维修中、3已报废。
 */
export function createEquipment(data) {
  return request({ url: '/equipment', method: 'post', data })
}

/** 完整更新；data 字段与 EquipmentSaveRequest 相同。 */
export function updateEquipment(id, data) {
  return request({ url: `/equipment/${encodeURIComponent(id)}`, method: 'put', data })
}

export function deleteEquipment(id) {
  return request({ url: `/equipment/${encodeURIComponent(id)}`, method: 'delete' })
}

/** Binary downloads reuse request auth snapshots and JSON-error handling. */
export function exportEquipment(params) {
  return request({
    url: '/equipment/export',
    method: 'get',
    params,
    responseType: 'blob',
    expectedFileType: 'xlsx',
  })
}
export function getEquipmentImportTemplate() {
  return request({
    url: '/equipment/import-template',
    method: 'get',
    responseType: 'blob',
    expectedFileType: 'xlsx',
  })
}
export function importEquipment(file) {
  const data = new FormData()
  data.append('file', file)
  return request({ url: '/equipment/import', method: 'post', data, timeout: 60000 })
}
