import { createSSRApp, h } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it } from 'vitest'
import StateEmpty from '../src/components/common/StateEmpty.vue'
import StateError from '../src/components/common/StateError.vue'
import StateLoading from '../src/components/common/StateLoading.vue'

function render(component, props = {}, slots = {}) {
  const app = createSSRApp({ render: () => h(component, props, slots) })
  app.component('ElIcon', { render() { return h('span', {}, this.$slots.default?.()) } })
  app.component('ElButton', {
    props: ['disabled'],
    render() { return h('button', { disabled: this.disabled }, this.$slots.default?.()) },
  })
  app.component('ElSkeleton', { props: ['rows'], render() { return h('div', { 'data-rows': this.rows }) } })
  return renderToString(app)
}

describe('shared asynchronous feedback', () => {
  it('announces an empty result with a clear next action', async () => {
    const html = await render(StateEmpty, { title: '暂无预约', description: '选择设备后提交申请', actionLabel: '选择设备' })
    expect(html).toContain('role="status"')
    expect(html).toContain('<h3>暂无预约</h3>')
    expect(html).toContain('选择设备后提交申请')
    expect(html).toMatch(/<button\b[^>]*>选择设备<\/button>/)
  })

  it('keeps a caller-provided navigation action instead of duplicating a default button', async () => {
    const html = await render(StateEmpty, { actionLabel: '默认操作' }, { default: () => h('a', { href: '/equipment' }, '浏览设备') })
    expect(html).toContain('href="/equipment"')
    expect(html).not.toContain('默认操作')
  })

  it('announces an error, escapes server text and prevents retry while a request is pending', async () => {
    const html = await render(StateError, { description: '<script>unsafe</script>', disabled: true, retryLabel: '重试' })
    expect(html).toContain('role="alert"')
    expect(html).toContain('&lt;script&gt;unsafe&lt;/script&gt;')
    expect(html).toContain('<button disabled>重试</button>')
    expect(html).not.toContain('<script>')
  })

  it('exposes one loading announcement and hides decorative skeleton content from assistive technology', async () => {
    const html = await render(StateLoading, { rows: 5, label: '正在加载预约…' })
    expect(html).toContain('role="status"')
    expect(html).toContain('aria-busy="true"')
    expect(html).toContain('正在加载预约…')
    expect(html).toContain('aria-hidden="true"')
    expect(html).toContain('data-rows="5"')
  })
})
