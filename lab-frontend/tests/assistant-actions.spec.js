import { describe, it, expect } from 'vitest'
import { createRequire } from 'node:module'
import { assistantAction, assistantPlan, assistantShortcuts } from '../src/utils/assistant-actions'
const require = createRequire(import.meta.url)
const mini = require('../../lab-miniprogram/utils/assistant-actions.js')

describe('assistant navigation contract', () => {
  it('opens a student form but only the manager review page', () => {
    expect(assistantPlan('我要预约设备', 'student').auto).toMatchObject({path:'/reservations', query:{action:'apply'}})
    for(const role of ['admin','lab_admin']) expect(assistantPlan('我要预约设备', role).auto).toMatchObject({path:'/reservation-review', query:undefined})
  })
  it('distinguishes navigation, policy questions, and destructive requests', () => {
    for(const q of ['预约','怎么预约设备？','如何预约','请帮我预约','我要提交报修']) expect(assistantPlan(q,'student').auto).not.toBeNull()
    for(const q of ['不要预约','为什么预约被取消','怎么取消预约','怎么预约和签到？','预约并报修','预约规则是什么','帮我整理报修描述：开机黑屏','打开https://example.org']) expect(assistantPlan(q,'student').auto).toBeNull()
    expect(assistantPlan('为什么预约被取消','student').actions[0].code).toBe('reservations')
  })
  it('only resolves registered role-permitted destinations', () => {
    for(const code of ['https://evil.test','__proto__','constructor','users','cancel','accept']) expect(assistantAction(code,'student')).toBeNull()
    expect(assistantAction('stats','student')).toBeNull()
    expect(assistantPlan('我要预约','unknown').actions).toEqual([])
    expect(assistantShortcuts('unknown')).toEqual([])
  })
  it('web and mini share intent decisions and registered destinations', () => {
    const app=require('../../lab-miniprogram/app.json')
    for(const role of ['student','admin','lab_admin','unknown']) for(const q of ['我要预约设备','怎么预约','签到规则','查看我的预约','帮我报修','打开数据统计','不要预约','取消预约']) {
      const plan=assistantPlan(q,role);expect(mini.assistantPlan(q,role)).toEqual(plan)
      for(const a of plan.actions) expect(app.pages).toContain(a.mini.slice(1))
    }
  })
})
