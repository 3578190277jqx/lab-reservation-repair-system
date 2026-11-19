export const studentNavigation = [
  { path: '/home', label: '实验首页' },
  { path: '/equipment', label: '找设备' },
  { path: '/reservations', label: '我的预约' },
  { path: '/repairs', label: '我的报修' },
  { path: '/notifications', label: '我的通知' },
  {path:'/assistant',label:'操作助手'},
  { path: '/labs', label: '实验室指南' },
]
export function roleTitle(path, role, fallback = '') {
  if (role === 'student') return studentNavigation.find(item => item.path === path)?.label || fallback
  return { '/home': '运维工作台', '/equipment': '设备台账', '/labs': '实验室管理', '/repairs': '工单中心', '/reservation-review': '预约审核' }[path] || fallback
}
