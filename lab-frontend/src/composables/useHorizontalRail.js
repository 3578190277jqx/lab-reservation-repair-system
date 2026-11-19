import { nextTick, onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import { nextRailPosition, railBounds } from '@/utils/horizontal-rail'

export function useHorizontalRail(track, count) {
  const bounds = shallowRef({ start: true, end: true, maximum: 0 })
  const visibleRange = shallowRef('')
  let observer, frame = 0, disposed = false
  function update() {
    frame = 0
    const element = track.value
    if (!element || disposed) return
    bounds.value = railBounds(element.scrollLeft, element.clientWidth, element.scrollWidth)
    const viewport = element.getBoundingClientRect()
    const visible = [...element.children].map((card, index) => ({ rect: card.getBoundingClientRect(), index }))
      .filter(({ rect }) => rect.right > viewport.left + 2 && rect.left < viewport.right - 2)
    visibleRange.value = visible.length ? `当前可见第 ${visible[0].index + 1} 至 ${visible.at(-1).index + 1} 台，共 ${count.value} 台` : ''
  }
  function queueUpdate() { if (!disposed && !frame) frame = requestAnimationFrame(update) }
  function scroll(direction) {
    const element = track.value
    if (!element) return
    const viewport = element.getBoundingClientRect()
    const starts = [...element.children].map(card => card.getBoundingClientRect().left - viewport.left + element.scrollLeft)
    const maximum = Math.max(0, element.scrollWidth - element.clientWidth)
    element.scrollTo({ left: nextRailPosition(element.scrollLeft, starts, maximum, direction), behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  }
  function keydown(event) {
    if (event.target !== event.currentTarget || event.altKey || event.ctrlKey || event.metaKey || event.isComposing) return
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault()
      scroll(event.key === 'ArrowRight' ? 1 : -1)
    }
  }
  watch(count, async () => { await nextTick(); queueUpdate() })
  onMounted(() => {
    track.value?.addEventListener('scroll', queueUpdate, { passive: true })
    window.addEventListener('resize', queueUpdate, { passive: true })
    if (typeof ResizeObserver !== 'undefined' && track.value) {
      observer = new ResizeObserver(queueUpdate)
      observer.observe(track.value)
    }
    update()
  })
  onBeforeUnmount(() => {
    disposed = true
    track.value?.removeEventListener('scroll', queueUpdate)
    window.removeEventListener('resize', queueUpdate)
    observer?.disconnect()
    if (frame) cancelAnimationFrame(frame)
  })
  return { bounds, visibleRange, scroll, keydown }
}
