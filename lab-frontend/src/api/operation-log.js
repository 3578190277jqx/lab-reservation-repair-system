import request from '@/utils/request'
/** Admin only. No request bodies or credentials are displayed or queried. */
export function getOperationLogPage(params) {
  return request({ url: '/operation-log/page', method: 'get', params })
}
