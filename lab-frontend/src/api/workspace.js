import request from '@/utils/request'

/** 首页仅发出当前角色允许的请求；每块独立报错，失败不能当成零条数据。 */
export async function getWorkspace(role, { repairStatus = 0 } = {}) {
  let tasks
  if (role === 'student') {
    tasks = {
      equipment: { url: '/equipment/page', params: { current: 1, size: 4, status: 0 } },
      reservations: { url: '/reservation/my', params: { current: 1, size: 3 } },
      repairs: { url: '/repair/my', params: { current: 1, size: 3 } },
    }
  } else if (['admin', 'lab_admin'].includes(role)) {
    tasks = {
      overview: { url: '/stats/overview' },
      reservations: { url: '/reservation/page', params: { current: 1, size: 5, status: 0 } },
      repairs: { url: '/repair/page', params: { current: 1, size: 5, status: repairStatus } },
    }
  } else throw new Error('当前角色不能访问工作台')
  const keys = Object.keys(tasks)
  const results = await Promise.allSettled(
    keys.map((key) => request({ method: 'get', silent: true, ...tasks[key] })),
  )
  const data = {},
    errors = {}
  results.forEach((result, index) => {
    const key = keys[index]
    if (result.status === 'rejected') errors[key] = result.reason?.message || '加载失败，请重试'
    else if (
      !result.value ||
      (key !== 'overview' &&
        (!Array.isArray(result.value.records) || !Number.isFinite(Number(result.value.total))))
    )
      errors[key] = '服务返回的数据格式不正确'
    else data[key] = result.value
  })
  return { data, errors }
}
