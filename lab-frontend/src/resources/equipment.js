/** equipment 列表的展示与筛选定义，不包含请求或业务状态。 */
export default {
  kicker: 'EQUIPMENT',
  description: '查找实验设备，了解型号、归属实验室与当前状态。',
  searchLabel: '设备名称',
  searchKey: 'name',
  searchPlaceholder: '输入设备名称',
  statuses: [
    [0, '可用', 'success'],
    [1, '已预约', 'primary'],
    [2, '维修中', 'warning'],
    [3, '已报废', 'info'],
  ],
  columns: [
    ['id', '编号', 88],
    ['equipmentCode', '设备编码', 150],
    ['name', '设备名称', 190],
    ['model', '型号', 150],
    ['labName', '实验室', 160],
    ['labLocation', '位置', 160],
    ['manufacturer', '制造商', 170],
    ['purchaseDate', '采购日期', 130],
  ],
}
