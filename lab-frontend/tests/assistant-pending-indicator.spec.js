import { describe, expect, it } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import AssistantMessages from '@/components/assistant/AssistantMessages.vue'

async function renderMessage(loading) {
  const messages = [
    {
      id: 1,
      question: '怎样预约设备？',
      loading,
      answer: loading ? '' : '请查看排期。',
      mode: 'answer',
      actions: [],
    },
  ]
  const app = createSSRApp({ render: () => h(AssistantMessages, { messages }) })
  app.component('ElButton', { render: () => h('button') })
  return renderToString(app)
}

describe('assistant request feedback', () => {
  it('shows Element Plus loading feedback while an answer is pending', async () => {
    const html = await renderMessage(true)
    expect(html).toContain('data-assistant-pending')
    expect(html).toContain('el-icon')
    expect(html).toContain('aria-busy="true"')
    expect(html).toContain('正在回答')
    expect(html).not.toContain('请查看排期。')
  })

  it('removes the pending indicator when a response finishes', async () => {
    const html = await renderMessage(false)
    expect(html).not.toContain('data-assistant-pending')
    expect(html).toContain('aria-busy="false"')
    expect(html).toContain('请查看排期。')
  })
})
