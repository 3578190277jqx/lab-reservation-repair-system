/** user 列表的展示与筛选定义，不包含请求或业务状态。 */
export default {
  kicker: 'MEMBERS',
  description: '查看平台用户、角色及账号状态。',
  searchLabel: '用户名',
  searchKey: 'username',
  searchPlaceholder: '输入用户名',
  statuses: [
    [1, '正常', 'success'],
    [0, '禁用', 'danger'],
  ],
  columns: [
    ['id', '用户编号', 110],
    ['username', '用户名', 160],
    ['realName', '真实姓名', 140],
    ['role', '角色', 150],
    ['studentNo', '学号', 150],
    ['phone', '联系电话', 150],
    ['email', '邮箱', 210],
    ['lastLoginTime', '最近登录', 180],
  ],
}
