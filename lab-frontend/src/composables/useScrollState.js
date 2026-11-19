import { onBeforeUnmount, onMounted, shallowRef } from 'vue'

export function useScrollState() {
  const scrolled = shallowRef(false)
  const showBackTop = shallowRef(false)
  let frame = 0
  function update() {
    frame = 0
    scrolled.value = window.scrollY > 16
    showBackTop.value = window.scrollY > 540
  }
  function queueUpdate() { if (!frame) frame = window.requestAnimationFrame(update) }
  function backToTop() {
    window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  }
  onMounted(() => { update(); window.addEventListener('scroll', queueUpdate, { passive: true }) })
  onBeforeUnmount(() => { window.removeEventListener('scroll', queueUpdate); if (frame) window.cancelAnimationFrame(frame) })
  return { scrolled, showBackTop, backToTop }
}
