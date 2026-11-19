const session = require('./session')
// Native tab bar keeps WeChat's safe-area and touch behavior.
function apply(section) {
  const manager = session.manager()
  const tabs = manager ? ['工作台', '设备', '审核', '工单', '我的'] : ['首页', '设备', '预约', '报修', '我的']
  const keys = ['home', 'equipment', 'reservations', 'repairs', 'profile']
  const accent = manager ? '#315ee7' : '#087f70'
  if (wx.setTabBarStyle) wx.setTabBarStyle({ color: '#7a8696', selectedColor: accent, backgroundColor: '#ffffff', borderStyle: 'white' })
  if (wx.setTabBarItem) keys.forEach((key, index) => wx.setTabBarItem({ index, text: tabs[index],
    iconPath: 'assets/tabs/' + key + '.png', selectedIconPath: 'assets/tabs/' + key + (manager ? '-admin.png' : '-student.png') }))
  if (wx.setNavigationBarColor) wx.setNavigationBarColor({ frontColor: '#000000', backgroundColor: '#f5f7fa' })
  if (wx.setNavigationBarTitle && keys.includes(section)) wx.setNavigationBarTitle({ title: tabs[keys.indexOf(section)] })
  return manager
}
module.exports = { apply }
