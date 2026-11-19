/** repair 列表的展示与筛选定义，不包含请求或业务状态。 */
export default {
  kicker: 'REPAIR ORDERS',
  description: '查看故障描述与工单处理进展。',
  searchLabel: '设备 ID',
  searchKey: 'equipmentId',
  searchPlaceholder: '输入设备编号',
  numeric: true,
  statuses: [
    [0, '待受理', 'warning'],
    [1, '已受理', 'primary'],
    [2, '维修中', 'warning'],
    [3, '已完成', 'success'],
  ],
  columns: [
    ['orderNo', '工单编号', 230],
    ['equipmentName', '设备名称', 180],
    ['labName', '实验室', 160],
    ['reporterId', '报修人 ID', 120],
    ['description', '故障描述', 250],
    ['handlerId', '处理人 ID', 120],
    ['createTime', '报修时间', 180],
    ['completedTime', '完成时间', 180],
  ],
}
