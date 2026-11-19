function reservationTimeline(row) {
  const events = []
  if (row.applyTime || row.createTime) events.push({ title: '提交申请', time: row.applyTime || row.createTime, content: row.purpose || '' })
  if (row.auditTime && row.status !== 5) events.push({ title: row.status === 2 ? '审核拒绝' : '审核处理', time: row.auditTime, content: row.auditRemark || '审核已完成' })
  // 自动超时不是人工审核；处理时间来自后端事务更新，缺失时不编造时间。
  if (row.status === 5 && row.updateTime) events.push({ title: '审核超时', time: row.updateTime, content: row.auditRemark || '预约开始仍未审核，系统自动关闭' })
  if (row.signInTime) events.push({ title: '到场签到', time: row.signInTime, content: '系统已记录签到' })
  if (row.cancelTime) events.push({ title: '预约取消', time: row.cancelTime, content: row.cancelReason || '未填写取消原因' })
  if (row.noShowTime) events.push({ title: '标记爽约', time: row.noShowTime, content: '未在规定窗口签到' })
  return events.sort((a,b) => a.time.localeCompare(b.time))
}
module.exports = { reservationTimeline }
