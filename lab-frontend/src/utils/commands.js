import { roleTitle, studentNavigation } from './role-ui'

export function navigationCommands(role) {
  if (!['student', 'admin', 'lab_admin'].includes(role)) return []
  const links = role === 'student' ? studentNavigation : [
    { path: '/home', label: '工作台' },
    { path: '/equipment', label: '设备台账' },
    { path: '/labs', label: '实验室管理' },
    { path: '/reservation-review', label: '预约审核' },
    { path: '/repairs', label: '工单中心' },
    { path: '/stats', label: '统计分析' },
    ...(role === 'admin' ? [{ path: '/users', label: '用户管理' }] : []),
    { path: '/assistant', label: '操作助手' },
  ]
  const aliases = {
    '/home': 'home 首页 工作台',
    '/equipment': 'equipment 仪器 设备 查询',
    '/labs': 'lab 实验室 位置 联系',
    '/reservations': 'reservation 我的预约 签到 取消',
    '/reservation-review': 'reservation 预约 审核 通过 拒绝',
    '/repairs': 'repair 故障 报修 工单 维修',
    '/stats': 'statistics 数据 统计 看板',
    '/users': 'user 用户 角色',
    '/assistant': 'assistant AI 助手 问答',
  }
  return [
    ...(role === 'student' ? [{
      id: 'apply', label: '申请设备预约', detail: '选择设备和时间，再确认提交',
      keywords: '预约 申请 申请预约 新建 reservation', type: 'action',
      to: { path: '/reservations', query: { action: 'apply' } },
    }] : []),
    {
      id: 'report', label: '提交故障报修', detail: '填写故障信息，跟进维修进度',
      keywords: '维修 报修 故障 新建 repair', type: 'action',
      to: { path: '/repairs', query: { action: 'submit' } },
    },
    ...links.map(item => ({
      id: item.path, label: roleTitle(item.path, role, item.label),
      detail: '前往页面', keywords: aliases[item.path] || '',
      type: 'page', to: { path: item.path },
    })),
    { id: 'profile', label: '个人信息', detail: '查看账号与绑定信息', keywords: 'profile 账号 邮箱 密码 联系', type: 'page', to: { path: '/profile' } },
  ]
}

export function filterCommands(commands, query) {
  const tokens = String(query || '').normalize('NFKC').trim().toLocaleLowerCase().split(/\s+/).filter(Boolean)
  return commands.filter(item => {
    const text = [item.label, item.detail, item.keywords].join(' ').normalize('NFKC').toLocaleLowerCase()
    return tokens.every(token => text.includes(token))
  })
}

export function moveCommandIndex(index, delta, count) {
  if (count <= 0) return -1
  const current = Number.isInteger(index) ? index : 0
  return ((current + delta) % count + count) % count
}
