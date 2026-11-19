// 模型输出和用户输入中的 URL 永远不会用于跳转，仅允许固定动作。
const roles = ['student', 'admin', 'lab_admin']
function assistantAction(code, role) {
  if (!roles.includes(role)) return null
  const student = role === 'student'
  const actions = {
    reserve: { code: 'reserve', label: student ? '申请设备预约' : '打开预约审核', hint: student ? '选择设备、时间和用途后，由你确认提交。' : '当前为管理员账号，请在审核页处理学生预约。', path: student ? '/reservations' : '/reservation-review', query: student ? { action: 'apply' } : undefined, mini: student ? '/pages/equipment/index' : '/pages/reservations/index', tab: true },
    reservations: { code: 'reservations', label: student ? '查看我的预约 / 签到' : '查看预约审核', hint: '在预约列表查看状态，再选择具体记录操作。', path: student ? '/reservations' : '/reservation-review', mini: '/pages/reservations/index', tab: true },
    report: { code: 'report', label: '提交故障报修', hint: '选择设备并填写故障描述后，由你确认提交。', path: '/repairs', query: { action: 'submit' }, mini: '/pages/equipment/index', tab: true },
    repairs: { code: 'repairs', label: student ? '查看我的报修' : '打开工单处理', hint: '查看工单状态和维修进度。', path: '/repairs', mini: '/pages/repairs/index', tab: true },
    equipment: { code: 'equipment', label: '浏览实验设备', hint: '在设备列表选择预约或报修。', path: '/equipment', mini: '/pages/equipment/index', tab: true },
    profile: { code: 'profile', label: '打开个人信息', hint: '查看和维护个人资料。', path: '/profile', mini: '/pages/profile/index', tab: true },
    stats: { code: 'stats', label: '打开数据统计', hint: '查看管理范围内的统计看板。', path: '/stats', mini: '/pages/stats/index', tab: false },
  }
  if (code === 'stats' && student) return null
  return Object.prototype.hasOwnProperty.call(actions, code) ? actions[code] : null
}
function assistantShortcuts(role) {
  return ['reserve', 'report', 'repairs', 'equipment'].map(code => assistantAction(code, role)).filter(Boolean)
}
function assistantPlan(question, role) {
  const text = String(question || '').replace(/\s+/g, '').replace(/[。！？?!]+$/g, '')
  if (!roles.includes(role)) return { actions: [], auto: null }
  let codes = []
  if (/预约|签到|爽约|黑名单/.test(text)) {
    codes = /我的预约|预约记录|预约进度|签到|取消|审核|审批|爽约|黑名单|拒绝|被拒|冲突|失败/.test(text) ? ['reservations', 'reserve'] : ['reserve', 'reservations']
  }
  if (/报修|维修|工单|故障/.test(text)) {
    codes.push(...(/进度|记录|我的报修|处理|受理|维修中|完成|工单/.test(text) ? ['repairs', 'report'] : ['report', 'repairs']))
  }
  if (!codes.length && /设备/.test(text)) codes = ['equipment']
  if (!codes.length && /个人信息|个人资料/.test(text)) codes = ['profile']
  if (!codes.length && /统计|看板/.test(text)) codes = ['stats']
  const actions = [...new Set(codes)].map(code => assistantAction(code, role)).filter(Boolean)
  // 否定句、规则咨询和复合问题只展示按钮；明确的入门/导航请求才自动前往。
  const plain = text.replace(/^(?:请问|请|你好[，,]?)/, '')
  const navigation = /^(?:(?:我想|我要|我想要|帮我|带我|去|打开|进入|跳转到|如何|怎么|怎样|在哪里|在哪)(?:去|进行)?(?:申请设备预约|预约设备|设备预约|申请预约|预约|我的预约|预约记录|预约审核|签到|报修|提交报修|故障报修|我的报修|维修进度|报修进度|工单|设备列表|个人信息|数据统计)|申请预约|预约设备|设备预约|预约|提交报修|故障报修|报修)(?:页面|界面|入口)?$/.test(plain)
  const mixed = /预约/.test(text) && /报修|维修|工单|故障|签到/.test(text)
  return { actions, auto: navigation && !mixed && actions.length ? actions[0] : null }
}
module.exports = { assistantAction, assistantShortcuts, assistantPlan }
