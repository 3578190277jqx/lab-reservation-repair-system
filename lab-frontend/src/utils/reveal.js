export function startReveal(element, environment = globalThis) {
  let observer, media
  const cleanup = () => {
    observer?.disconnect()
    media?.removeEventListener?.('change', onPreferenceChange)
    if (element?.dataset) element.dataset.reveal = 'visible'
  }
  function onPreferenceChange() { if (media.matches) cleanup() }
  if (!element?.dataset) return cleanup
  element.dataset.reveal = 'visible'
  try {
    media = environment.matchMedia?.('(prefers-reduced-motion: reduce)')
    if (!media || media.matches || !environment.IntersectionObserver
      || element.getBoundingClientRect().top < environment.innerHeight - 30) return cleanup
    element.dataset.reveal = 'pending'
    observer = new environment.IntersectionObserver(entries => {
      if (entries.some(entry => entry.target === element && entry.isIntersecting)) cleanup()
    }, { threshold: 0.04, rootMargin: '0px 0px 30px 0px' })
    media.addEventListener?.('change', onPreferenceChange)
    observer.observe(element)
  } catch { cleanup() }
  return cleanup
}

const controllers = new WeakMap()
export const revealDirective = {
  mounted(element) { controllers.set(element, startReveal(element, window)) },
  beforeUnmount(element) { controllers.get(element)?.(); controllers.delete(element) },
}
