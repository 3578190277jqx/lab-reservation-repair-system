const api = require('../api/index')
const ui = require('./ui')
function page(value, labels) {
  if (!value || !Array.isArray(value.records) || value.total == null || !Number.isFinite(Number(value.total)) || Number(value.total) < 0) throw new Error('列表数据格式异常，请重试')
  return { total: Number(value.total), rows: value.records.map(row => ({ ...row, statusText: labels[row.status] || '未知状态' })) }
}
// Each panel fails independently: failed requests never turn into a zero statistic.
async function load(manager) {
  const query = { current: 1, size: 3 }
  const jobs = manager ? [
    ['overview', () => api.overview(), value => {
      if (!value || ['equipmentCount', 'availableEquipmentCount', 'repairingEquipmentCount'].some(k => value[k] == null || !Number.isFinite(Number(value[k])) || Number(value[k]) < 0)) throw new Error('设备概况格式异常，请重试')
      return value
    }],
    ['reservations', () => api.reservationPage({ ...query, status: 0 }), v => page(v, ui.reservationStatus)],
    ['repairs', () => api.repairPage({ ...query, status: 0 }), v => page(v, ui.repairStatus)]
  ] : [
    ['equipment', () => api.equipment({ ...query, size: 4, status: 0 }), v => page(v, ui.equipmentStatus)],
    ['reservations', () => api.reservations(query), v => page(v, ui.reservationStatus)],
    ['repairs', () => api.repairs(query), v => page(v, ui.repairStatus)]
  ]
  const results = await Promise.all(jobs.map(async ([key, request, transform]) => {
    try { return { key, value: transform(await request()) } }
    catch (error) { return { key, error: error.message || '加载失败，请重试' } }
  }))
  const result = { equipment: null, overview: null, reservations: null, repairs: null, errors: {} }
  results.forEach(item => { if (item.error) result.errors[item.key] = item.error; else result[item.key] = item.value })
  return result
}
module.exports = { load }
