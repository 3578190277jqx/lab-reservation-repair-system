/** lab 列表的展示与筛选定义，不包含请求或业务状态。 */
export default {
  kicker: 'LABORATORIES',
  description: '查看实验室的位置、联系方式与开放状态。',
  searchLabel: '实验室名称',
  searchKey: 'name',
  searchPlaceholder: '输入实验室名称',
  statuses: [
    [1, '启用', 'success'],
    [0, '停用', 'info'],
  ],
  columns: [
    ['id', '编号', 88],
    ['labCode', '实验室编码', 140],
    ['name', '实验室名称', 190],
    ['location', '位置', 180],
    ['adminId', '管理员 ID', 120],
    ['contactPhone', '联系电话', 150],
    ['description', '简介', 220],
  ],
}
