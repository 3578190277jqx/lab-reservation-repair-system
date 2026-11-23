import { describe, expect, it } from 'vitest'
import reservation from '../src/resources/reservation'
import { reservationStatusName, reservationTimeline } from '../src/utils/reservation-detail'
import { rowActions } from '../src/utils/workflow'

describe('审核超时预约', () => {
  it('状态可用于列表筛选与展示，不能继续审核、取消或签到', () => {
    expect(reservation.statuses.find(([code]) => code === 5)).toEqual([5, '审核超时', 'info'])
    expect(reservationStatusName(5)).toBe('审核超时')
    expect(reservationStatusName(99)).toBe('未知状态')
    const row = {
      status: 5,
      studentId: 7,
      startTime: '2026-10-03 09:00:00',
      endTime: '2026-10-03 10:00:00',
    }
    expect(rowActions('reservation', row, { role: 'admin' })).toEqual([])
    expect(rowActions('reservation', row, { role: 'student', id: 7 })).toEqual([])
  })

  it('以系统处理时间与原因呈现超时，不伪造管理员审核或学生爽约', () => {
    const events = reservationTimeline({
      status: 5,
      applyTime: '2026-10-03 08:00:00',
      updateTime: '2026-10-03 09:00:45',
      auditRemark: '预约开始仍未审核，系统自动关闭',
      auditTime: null,
      auditBy: null,
    })
    expect(events).toEqual([
      { title: '提交申请', time: '2026-10-03 08:00:00', content: '' },
      { title: '审核超时', time: '2026-10-03 09:00:45', content: '预约开始仍未审核，系统自动关闭' },
    ])
    expect(reservationTimeline({ status: 5 })).toEqual([])
  })
})
