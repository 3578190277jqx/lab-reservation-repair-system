import { describe, expect, it } from 'vitest'
import { actionPayload, beijingTime, isPastReservationDate, reservationTimeError, tomorrowReservationRange, rowActions, validateImage } from '../src/utils/workflow'

const student = { id: 7, role: 'student' }, manager = { id: 2, role: 'lab_admin' }
const now = beijingTime('2026-10-03 09:00:00')
describe('预约与报修操作规则', () => {
  it('学生可申请可用或已预约设备，管理员仅可报修，报废设备不可操作', () => {
    expect(rowActions('equipment', { status: 1 }, student)).toEqual(['apply','submit'])
    expect(rowActions('equipment', { status: 0 }, manager)).toEqual(['submit'])
    expect(rowActions('equipment', { status: 2 }, student)).toEqual(['submit'])
    expect(rowActions('equipment', { status: 3 }, student)).toEqual([])
  })
  it('取消和签到限定本人、状态及开始后30分钟的窗口', () => {
    const row = { studentId: 7, status: 1, startTime: '2026-10-03 09:00:00', endTime: '2026-10-03 10:00:00' }
    expect(rowActions('reservation', row, student, now - 1)).toEqual(['cancel'])
    expect(rowActions('reservation', row, student, now)).toEqual(['signIn'])
    expect(rowActions('reservation', row, student, now + 30*60000)).toEqual([])
    expect(rowActions('reservation', { ...row, endTime: '2026-10-03 09:10:00' }, student, now+10*60000)).toEqual([])
    expect(rowActions('reservation', { ...row, signInTime: row.startTime }, student, now)).toEqual([])
    expect(rowActions('reservation', { ...row, studentId: 8 }, student, now)).toEqual([])
  })
  it('只有待审核预约显示审核，维修继续操作限定受理人或超级管理员', () => {
    expect(rowActions('reservation', { status: 0 }, manager)).toEqual(['audit'])
    expect(rowActions('reservation', { status: 1 }, manager)).toEqual([])
    expect(rowActions('repair', { status: 0 }, manager)).toEqual(['records','accept'])
    expect(rowActions('repair', { status: 1, handlerId: 3 }, manager)).toEqual(['records'])
    expect(rowActions('repair', { status: 1, handlerId: 2 }, manager)).toEqual(['records','start'])
    expect(rowActions('repair', { status: 2, handlerId: 2 }, manager)).toEqual(['records','complete'])
    expect(rowActions('repair', { status: 1, handlerId: 3 }, { id: 1, role: 'admin' })).toEqual(['records','start'])
    expect(rowActions('repair', { status: 3 }, manager)).toEqual(['records'])
  })
  it('预约发送精确DTO，忽略伪造身份字段并按北京时间验证时段', () => {
    const form = { equipmentId: 1, times: ['2026-10-03 10:00:00','2026-10-03 11:00:00'], purpose: ' 实验 ', studentId: 99, status: 1 }
    expect(actionPayload('apply', form, {}, now)).toEqual({ equipmentId: 1, startTime: form.times[0], endTime: form.times[1], purpose: '实验' })
    expect(beijingTime('2026-10-03 09:00:00')).toBe(Date.parse('2026-10-03T01:00:00Z'))
    expect(() => actionPayload('apply', { ...form, times: ['2026-10-03 08:00:00','2026-10-03 09:00:00'] }, {}, now)).toThrow()
    expect(() => actionPayload('apply', { ...form, times: [form.times[1], form.times[0]] }, {}, now)).toThrow()
    expect(() => actionPayload('apply', { ...form, purpose: ' ' }, {}, now)).toThrow()
  })
  it('清楚区分未选时间、过去时段和结束早于开始', () => {
    expect(reservationTimeError([], now)).toContain('请选择预约开始时间和结束时间')
    expect(reservationTimeError(['2026-09-29 00:00:00', '2026-10-02 00:00:00'], now)).toContain('开始时间已过去')
    expect(reservationTimeError(['2026-10-03 09:00:00', '2026-10-03 10:00:00'], now)).toContain('开始时间已过去')
    expect(reservationTimeError(['2026-10-03 10:00:00', '2026-10-03 09:00:00'], now)).toContain('结束时间必须晚于开始时间')
    expect(reservationTimeError(['2026-10-03 10:00:00', '2026-10-03 11:00:00'], now)).toBe('')
  })
  it('按北京时间的日期禁用过去日历，不受电脑时区的日期偏移影响', () => {
    const midnight = Date.parse('2026-10-15T16:30:00Z')
    expect(isPastReservationDate(new Date(2026, 9, 15), midnight)).toBe(true)
    expect(isPastReservationDate(new Date(2026, 9, 16), midnight)).toBe(false)
    expect(isPastReservationDate(new Date(2026, 9, 17), midnight)).toBe(false)
  })
  it('明天快捷时段跨月也始终在未来，且采用北京时间', () => {
    const monthEnd = Date.parse('2026-10-31T15:59:00Z')
    expect(tomorrowReservationRange(monthEnd)).toEqual(['2026-11-01 09:00:00', '2026-11-01 10:00:00'])
    expect(reservationTimeError(tomorrowReservationRange(monthEnd), monthEnd)).toBe('')
    expect(tomorrowReservationRange(Date.parse('2026-10-31T16:01:00Z'))[0]).toBe('2026-11-02 09:00:00')
  })
  it('报修无图片仍可提交，无图片时不请求AI', () => {
    expect(actionPayload('submit', { equipmentId: 1, description: ' 黑屏 ', analyzeImage: true }, {})).toEqual({ equipmentId: 1, description: '黑屏', imageUrl: null, analyzeImage: false })
    expect(() => actionPayload('submit', { equipmentId: 1, description: '' }, {})).toThrow()
  })
  it('拒绝必须说明原因，维修完成必须填结果和合法费用', () => {
    expect(() => actionPayload('audit', { approved: false, remark: '' }, { id: 4 })).toThrow()
    expect(actionPayload('audit', { approved: true }, { id: 4 })).toEqual({ reservationId: 4, approved: true, remark: '' })
    expect(actionPayload('complete', { content: ' 已修复 ', repairCost: '12.50' }, { id: 5 })).toEqual({ repairOrderId: 5, content: '已修复', repairCost: '12.50' })
    for (const value of ['-1','NaN','1.234','100000000','']) expect(() => actionPayload('complete', { content: '修复', repairCost: value }, { id: 5 })).toThrow()
    expect(() => actionPayload('complete', { content: '', repairCost: '0' }, { id: 5 })).toThrow()
  })
  it('图片限制匹配后端，非法文件不发起上传', () => {
    expect(() => validateImage({ type: 'image/png', size: 5*1024*1024 })).not.toThrow()
    for (const file of [{ type: 'image/gif', size: 500 }, { type: 'image/png', size: 0 }, { type: 'image/jpeg', size: 5*1024*1024+1 }]) expect(() => validateImage(file)).toThrow()
  })
})
