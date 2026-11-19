import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, reactive } from 'vue'
import { useAssistantConversation } from '../src/composables/useAssistantConversation'
import { buildAssistantHistory } from '../src/utils/assistant-conversation'
import { createRequire } from 'node:module'
const mini = createRequire(import.meta.url)('../../lab-miniprogram/utils/assistant-conversation.js')
const scopes = []
afterEach(() => { scopes.splice(0).forEach(s => s.stop()); vi.useRealTimers() })
function setup() {
  const identity = reactive({ token: 'A', role: 'student' }), calls = [], waiting = []
  const request = config => {
    if (config.url.endsWith('/options')) return Promise.resolve({ enabled: true })
    calls.push(config.data)
    return new Promise((resolve, reject) => waiting.push({ resolve, reject }))
  }
  const navigate = vi.fn(), scope = effectScope(); scopes.push(scope)
  const c = scope.run(() => useAssistantConversation({ session: () => identity, request, navigate }))
  const ask = async (q, mode = 'model', answer = '设备黑屏，请联系管理员') => {
    const promise = c.ask(q); waiting.at(-1).resolve({ mode, answer }); await promise
  }
  return { c, calls, waiting, identity, scope, ask, navigate }
}
describe('bounded assistant conversation', () => {
  it('sends last three successful model pairs with identical web/mini contract', () => {
    const messages = [1, 2, 3, 4].map(i => ({ question: `问${i}`, answer: `答${i}`, mode: 'model' }))
    messages.push({ question: '失败', answer: '指南', mode: 'unavailable' }, { question: '预约', answer: '跳转', mode: 'navigation' })
    const expected = [2, 3, 4].map(i => ({ question: `问${i}`, answer: `答${i}` }))
    expect(buildAssistantHistory(messages)).toEqual(expected)
    expect(mini.buildAssistantHistory(messages)).toEqual(expected)
    expect(buildAssistantHistory([{ mode: 'model', question: ' ', answer: '答' }, { mode: 'model', question: '问', answer: 'a'.repeat(4000) }])[0].answer).toHaveLength(3000)
  })
  it('follow-up includes prior question and answer', async () => {
    const { c, ask, calls } = setup()
    await ask('描述设备黑屏'); await ask('再简短一点')
    expect(calls[1]).toEqual({ question: '再简短一点', history: [{ question: '描述设备黑屏', answer: '设备黑屏，请联系管理员' }] })
    expect(c.messages.value).toHaveLength(2)
  })
  it('manual retry replaces failed turn without duplicating context or erasing draft', async () => {
    const { c, ask, waiting, calls } = setup(); await ask('先描述故障')
    let p = c.ask('缩短一点'); waiting.at(-1).reject(new Error('网络错误')); await p
    expect(c.messages.value.at(-1).retryable).toBe(true)
    c.question.value = '新的草稿'; p = c.retry()
    expect(calls[2]).toEqual(calls[1]); waiting.at(-1).resolve({ mode: 'model', answer: '黑屏待检' }); await p
    expect(c.messages.value).toHaveLength(2); expect(c.messages.value.at(-1).answer).toBe('黑屏待检')
    expect(c.question.value).toBe('新的草稿')
  })
  it.each(['clear', 'switch', 'dispose'])('%s discards history and late replies', async action => {
    const { c, ask, waiting, identity, scope } = setup(); await ask('之前的内容')
    const p = c.ask('继续')
    if (action === 'clear') c.clear()
    if (action === 'switch') identity.token = 'B'
    if (action === 'dispose') scope.stop()
    waiting.at(-1).resolve({ mode: 'model', answer: '过期的私密回答' }); await p
    expect(c.messages.value).toEqual([]); expect(c.busy.value).toBe(false)
  })
  it('unavailable responses can retry while guides never enter history', async () => {
    const { c, ask, calls } = setup()
    await ask('第一问', 'unavailable', '系统指南'); expect(c.canRetry.value).toBe(true)
    await ask('第二问', 'guide', '系统指南'); expect(c.canRetry.value).toBe(false)
    expect(calls[1].history).toEqual([])
  })
  it('navigation still bypasses model and countdown can be cancelled', async () => {
    vi.useFakeTimers(); const { c, calls, navigate } = setup()
    await c.ask('我要预约设备'); expect(calls).toEqual([])
    c.cancelNavigation(); vi.advanceTimersByTime(4000); expect(navigate).not.toHaveBeenCalled()
    await c.ask('我要预约设备'); vi.advanceTimersByTime(3000)
    expect(navigate).toHaveBeenCalledWith({ path: '/reservations', query: { action: 'apply' } })
  })
  it.each(['reject', 'cancel'])('failed navigation (%s) keeps in-flight answer usable', async outcome => {
    const { c, waiting, navigate } = setup()
    const p = c.ask('帮我整理设备故障')
    if (outcome === 'reject') navigate.mockRejectedValueOnce(new Error('route failed'))
    else navigate.mockResolvedValueOnce({ type: 4 })
    await c.go('equipment')
    expect(c.busy.value).toBe(true)
    expect(c.error.value).toContain('页面暂时无法打开')
    waiting.at(-1).resolve({ mode: 'model', answer: '保留回答' }); await p
    expect(c.messages.value[0]).toMatchObject({ answer: '保留回答', loading: false })
  })
})
