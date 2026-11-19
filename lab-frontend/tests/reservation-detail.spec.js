import {describe,it,expect} from 'vitest'
import {reservationTimeline} from '../src/utils/reservation-detail'
describe('预约进度',()=>{
 it('使用服务端审计时间排序，保留系统取消原因',()=>{const events=reservationTimeline({applyTime:'2026-10-10 08:00:00',auditTime:'2026-10-10 08:30:00',cancelTime:'2026-10-10 09:00:00',status:3,cancelReason:'设备进入维修'});expect(events.map(e=>e.title)).toEqual(['提交申请','审核处理','预约取消']);expect(events[2].content).toBe('设备进入维修')})
 it('拒绝、签到、爽约均依据实际字段，不臆造完成事件',()=>{expect(reservationTimeline({status:2,auditTime:'2026-10-10 08:00:00'})[0].title).toBe('审核拒绝');expect(reservationTimeline({status:1})).toEqual([]);expect(reservationTimeline({signInTime:'2026-10-10 08:00:00'})[0].title).toBe('到场签到')})
})
