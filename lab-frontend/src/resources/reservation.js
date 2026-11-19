/** reservation 列表的展示与筛选定义，不包含请求或业务状态。 */
export default {
  kicker: 'RESERVATIONS',
  description: '查看预约时段、用途与审核进展。',
  searchLabel: '设备 ID',
  searchKey: 'equipmentId',
  searchPlaceholder: '输入设备编号',
  numeric: true,
  statuses: [
    [0, '待审核', 'warning'],
    [1, '已通过', 'success'],
    [2, '已拒绝', 'danger'],
    [3, '已取消', 'info'],
    [4, '爽约', 'danger'],
    [5, '审核超时', 'info'],
  ],
  columns: [
    ['id', '预约编号', 110],
    ['equipmentName', '设备名称', 180],
    ['labName', '实验室', 160],
    ['studentId', '学生 ID', 110],
    ['startTime', '开始时间', 180],
    ['endTime', '结束时间', 180],
    ['purpose', '预约用途', 220],
    ['auditRemark', '处理说明', 200],
    ['signInTime', '签到时间', 180],
  ],
}
