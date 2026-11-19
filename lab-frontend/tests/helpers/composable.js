import { createRenderer, defineComponent } from 'vue'

/** Mount lifecycle-bound composables with Vue's real renderer and a minimal host. */
export function mountComposable(factory) {
  let result
  const renderer = createRenderer({
    createElement: () => ({}),
    createText: () => ({}),
    createComment: () => ({}),
    insert() {},
    remove() {},
    setText() {},
    setElementText() {},
    patchProp() {},
    parentNode: () => null,
    nextSibling: () => null,
  })
  const app = renderer.createApp(
    defineComponent({
      setup() {
        result = factory()
        return () => null
      },
    }),
  )
  app.mount({})
  return { result, unmount: () => app.unmount() }
}
export function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
