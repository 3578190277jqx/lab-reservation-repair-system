/** 只把成功的模型问答作为上下文；操作导航、错误和固定指南不混入模型历史。 */
function buildAssistantHistory(messages) {
  return messages.filter(item => item.mode === 'model'
    && typeof item.question === 'string' && item.question.trim()
    && typeof item.answer === 'string' && item.answer.trim())
    .slice(-3).map(item => ({ question: item.question.trim().slice(0, 1000), answer: item.answer.trim().slice(0, 3000) }))
}

const assistantModes = {
  model: 'AI生成 · 仅供参考', guide: '系统操作指南（未启用文字模型）',
  navigation: '系统导航', unavailable: '模型暂不可用 · 操作指南',
  busy: '助手繁忙 · 操作指南', error: '发送失败',
}

function readAssistantAnswer(response) {
  if (!response || !['model', 'guide', 'unavailable', 'busy'].includes(response.mode)
    || typeof response.answer !== 'string' || !response.answer.trim()) throw new Error('回复格式异常，请重试')
  return { mode: response.mode, answer: response.answer.slice(0, 3000), retryable: ['unavailable', 'busy'].includes(response.mode) }
}

module.exports = { buildAssistantHistory, assistantModes, readAssistantAnswer }
