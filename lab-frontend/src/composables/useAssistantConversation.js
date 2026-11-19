import { computed, onScopeDispose, readonly, ref, shallowRef, watch } from 'vue'
import { assistantAction, assistantPlan } from '@/utils/assistant-actions'
import { buildAssistantHistory, readAssistantAnswer } from '@/utils/assistant-conversation'

/** 当前页面的临时会话。离开、清空或身份变化时撤销在途请求和导航计时。 */
export function useAssistantConversation({ session, request, navigate }) {
  const question = shallowRef(''), messages = ref([]), busy = shallowRef(false)
  const error = shallowRef(''), enabled = shallowRef(null), pending = shallowRef(null)
  let active = true, version = 0, nextId = 0, optionsVersion = 0
  let timer = null, controller = null, retryContext = null
  const canRetry = computed(() => !busy.value && !!messages.value.at(-1)?.retryable)
  const current = (v, identity) => active && v === version && !!identity.token
    && identity.token === session().token && identity.role === session().role

  function cancelNavigation() { clearInterval(timer); timer = null; pending.value = null }
  function clear() {
    version++; controller?.abort(); controller = null; retryContext = null
    cancelNavigation(); messages.value = []; question.value = ''; error.value = ''; busy.value = false
  }
  async function go(code) {
    const action = assistantAction(code, session().role)
    cancelNavigation()
    if (!active || !session().token || !action) return
    const v = version, identity = { ...session() }
    // 路由可能失败或被守卫取消；真正离页时由scope清理在途回答。
    try {
      const failure = await navigate({ path: action.path, query: action.query })
      if (failure) throw new Error('Navigation cancelled')
    } catch { if (current(v, identity)) error.value = '页面暂时无法打开，请重试' }
  }
  function schedule(action) {
    const v = version, identity = { ...session() }
    cancelNavigation(); pending.value = { ...action, seconds: 3 }
    timer = setInterval(() => {
      if (!current(v, identity) || !pending.value) return cancelNavigation()
      const seconds = pending.value.seconds - 1
      if (seconds <= 0) go(action.code)
      else pending.value = { ...pending.value, seconds }
    }, 1000)
  }
  async function options() {
    const identity = { ...session() }, n = ++optionsVersion
    if (!identity.token) return
    try {
      const result = await request({ url: '/assistant/options', silent: true })
      if (active && n === optionsVersion && identity.token === session().token && identity.role === session().role) enabled.value = result?.enabled === true
    } catch { /* 保留未知状态，快捷操作仍然可用。 */ }
  }
  function append(item) {
    messages.value = [...messages.value.map(message => ({ ...message, retryable: false })), item].slice(-10)
  }
  function update(id, values) { messages.value = messages.value.map(item => item.id === id ? { ...item, ...values } : item) }

  async function send(context, preserveDraft) {
    const v = ++version, identity = { ...session() }
    const abort = new AbortController(); controller = abort
    busy.value = true; error.value = ''; cancelNavigation()
    update(context.id, { retryable: false, loading: true })
    try {
      const response = await request({ url: '/assistant/ask', method: 'post', data: { question: context.question, history: context.history }, timeout: 20000, silent: true, signal: abort.signal })
      if (!current(v, identity)) return
      const answer = readAssistantAnswer(response)
      update(context.id, { ...answer, loading: false })
      retryContext = answer.retryable ? context : null
      if (!preserveDraft && question.value.trim() === context.question) question.value = ''
    } catch (e) {
      if (!current(v, identity)) return
      update(context.id, { mode: 'error', answer: e.message || '发送失败，请稍后重试', retryable: true, loading: false })
      retryContext = context
    } finally { if (current(v, identity)) { busy.value = false; controller = null } }
  }
  async function ask(text) {
    if (!active || busy.value || !session().token) return
    const q = (typeof text === 'string' ? text : question.value).trim()
    if (!q || q.length > 1000) { error.value = '请输入1至1000字的问题'; return }
    cancelNavigation(); retryContext = null; error.value = ''
    const plan = assistantPlan(q, session().role), id = ++nextId
    if (plan.auto) {
      append({ id, question: q, mode: 'navigation', answer: `即将打开“${plan.auto.label}”页面。${plan.auto.hint}`, actions: plan.actions })
      question.value = ''; schedule(plan.auto); return
    }
    const context = { id, question: q, history: buildAssistantHistory(messages.value) }
    append({ id, question: q, mode: 'pending', answer: '', actions: plan.actions, loading: true })
    await send(context, false)
  }
  async function retry() {
    if (!active || !session().token || !canRetry.value || !retryContext) return
    await send(retryContext, true)
  }
  watch(() => [session().token, session().role], () => { clear(); enabled.value = null; options() }, { immediate: true, flush: 'sync' })
  onScopeDispose(() => { active = false; optionsVersion++; clear() })
  return { question, messages: readonly(messages), busy: readonly(busy), error: readonly(error), enabled: readonly(enabled), pending: readonly(pending), canRetry, ask, retry, clear, go, cancelNavigation }
}
