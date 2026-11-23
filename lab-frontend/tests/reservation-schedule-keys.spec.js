import { describe, expect, it } from 'vitest'
import { createRenderer, defineComponent, h, nextTick, ref, ssrContextKey } from 'vue'
import * as Vue from 'vue'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import ReservationSchedule from '@/components/ReservationSchedule.vue'

// Vite's node test runner imports the SSR render; compile the same source template for the client renderer.
const source = readFileSync(
  new URL('../src/components/ReservationSchedule.vue', import.meta.url),
  'utf8',
)
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'schedule-test' })
const template = compileTemplate({
  source: descriptor.template.content,
  filename: 'ReservationSchedule.vue',
  id: 'schedule-test',
  compilerOptions: { mode: 'function', bindingMetadata: script.bindings },
})
if (template.errors.length)
  throw new Error(`Schedule template compilation failed: ${template.errors}`)
ReservationSchedule.render = new Function('Vue', template.code)(Vue)

function mountSchedule(controller) {
  const makeNode = (type) => ({ type, children: [], parent: null, props: {} })
  const remove = (node) => {
    if (!node.parent) return
    const siblings = node.parent.children
    const index = siblings.indexOf(node)
    if (index !== -1) siblings.splice(index, 1)
    node.parent = null
  }
  const renderer = createRenderer({
    createElement: makeNode,
    createText: (text) => ({ ...makeNode('#text'), text }),
    createComment: (text) => ({ ...makeNode('#comment'), text }),
    insert(node, parent, anchor) {
      remove(node)
      node.parent = parent
      const index = anchor ? parent.children.indexOf(anchor) : -1
      if (index < 0) parent.children.push(node)
      else parent.children.splice(index, 0, node)
    },
    remove,
    setText: (node, text) => {
      node.text = text
    },
    setElementText: (node, text) => {
      node.text = text
      node.children = []
    },
    patchProp: (node, key, _old, value) => {
      node.props[key] = value
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => {
      if (!node.parent) return null
      return node.parent.children[node.parent.children.indexOf(node) + 1] || null
    },
  })
  const root = makeNode('root')
  const app = renderer.createApp(
    defineComponent({
      setup: () => () => h(ReservationSchedule, { equipmentId: 1, controller }),
    }),
  )
  // Vitest loads the SFC through Vite SSR; provide the per-render module registry.
  app.provide(ssrContextKey, { modules: new Set() })
  const warnings = []
  app.config.warnHandler = (message) => warnings.push(message)
  for (const name of ['ElDatePicker', 'ElButton', 'ElAlert', 'ElTag']) {
    app.component(name, {
      render() {
        return h('span', this.$slots.default?.())
      },
    })
  }
  app.mount(root)
  function rows(node = root) {
    return [node, ...node.children.flatMap(rows)].filter((item) => item.type === 'li')
  }
  return { rows, warnings, unmount: () => app.unmount() }
}

describe('reservation schedule slot identity', () => {
  it('preserves each distinct and repeated slot node when the response reorders', async () => {
    const a = { startTime: '2030-01-01 08:00:00', endTime: '2030-01-01 09:00:00', status: 0 }
    const b = { startTime: '2030-01-01 10:00:00', endTime: '2030-01-01 11:00:00', status: 1 }
    const schedule = ref({ slots: [a, b, { ...a }], bookable: true })
    const controller = {
      date: ref('2030-01-01'),
      schedule,
      loading: ref(false),
      error: ref(''),
      conflict: ref(false),
      load() {},
    }
    const mounted = mountSchedule(controller)
    try {
      const oldRows = mounted.rows()
      expect(oldRows).toHaveLength(3)
      schedule.value = { slots: [{ ...b }, { ...a }, { ...a }], bookable: true }
      await nextTick()
      const updated = mounted.rows()
      expect(updated[0] === oldRows[1]).toBe(true)
      expect(updated[1] === oldRows[0]).toBe(true)
      expect(updated[2] === oldRows[2]).toBe(true)
      expect(new Set(updated).size).toBe(3)
      expect(mounted.warnings.some((message) => message.includes('Duplicate keys'))).toBe(false)
    } finally {
      mounted.unmount()
    }
  })
})
