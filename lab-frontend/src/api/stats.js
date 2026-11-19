import request from '@/utils/request'

// StatsQuery: { startDate?, endDate?, labId?, limit? }。
// 日期格式 yyyy-MM-dd，必须成对；默认最近30天、最多366天；limit 默认10、范围1..100。
// config 可传 signal 等请求配置；所有函数直接返回解包后的图表 VO。
export function getOverview(params, config = {}) {
  return request({ ...config, url: '/stats/overview', method: 'get', params })
}

export function getEquipmentUsage(params, config = {}) {
  return request({ ...config, url: '/stats/equipment-usage', method: 'get', params })
}

export function getReservationTrend(params, config = {}) {
  return request({ ...config, url: '/stats/reservation-trend', method: 'get', params })
}

export function getRepairStatus(params, config = {}) {
  return request({ ...config, url: '/stats/repair-status', method: 'get', params })
}

export function getLabRanking(params, config = {}) {
  return request({ ...config, url: '/stats/lab-ranking', method: 'get', params })
}
