import { describe, expect, it, vi } from 'vitest'
import { filterCommands, moveCommandIndex, navigationCommands } from '../src/utils/commands'
import { startReveal } from '../src/utils/reveal'

describe('role-aware command navigation', () => {
  it('students can start their workflows without receiving management routes', () => {
    const commands = navigationCommands('student')
    expect(commands.find(item => item.id === 'apply').to).toEqual({ path: '/reservations', query: { action: 'apply' } })
    expect(commands.map(item => item.to.path)).not.toContain('/users')
    expect(commands.map(item => item.to.path)).not.toContain('/reservation-review')
    expect(commands.map(item => item.to.path)).not.toContain('/stats')
  })

  it('lab administrators cannot reach user management or student-only applications', () => {
    const commands = navigationCommands('lab_admin')
    expect(commands.some(item => item.to.path === '/reservation-review')).toBe(true)
    expect(commands.some(item => item.id === 'report')).toBe(true)
    expect(commands.some(item => item.to.path === '/users' || item.id === 'apply')).toBe(false)
    expect(navigationCommands('admin').some(item => item.to.path === '/users')).toBe(true)
  })

  it('unknown roles receive no commands', () => {
    expect(navigationCommands('guest')).toEqual([])
    expect(navigationCommands(null)).toEqual([])
  })

  it('searches labels and aliases without interpreting user input as a URL', () => {
    const commands = navigationCommands('student')
    expect(filterCommands(commands, '预约 申请').map(item => item.id)).toContain('apply')
    expect(filterCommands(commands, '申请预约').map(item => item.id)).toContain('apply')
    expect(filterCommands(commands, ' EQUIPMENT ').some(item => item.to.path === '/equipment')).toBe(true)
    expect(filterCommands(commands, 'https://evil.example')).toEqual([])
    expect(filterCommands(commands, '   ')).toEqual(commands)
  })

  it('wraps keyboard selection and handles an empty result list', () => {
    expect(moveCommandIndex(0, -1, 3)).toBe(2)
    expect(moveCommandIndex(2, 1, 3)).toBe(0)
    expect(moveCommandIndex(12, 1, 3)).toBe(1)
    expect(moveCommandIndex(0, 1, 0)).toBe(-1)
  })
})

function revealFixture({ reduced = false, observerSupported = true, top = 1200 } = {}) {
  let callback
  const media = { matches: reduced, addEventListener: vi.fn(), removeEventListener: vi.fn() }
  const observer = { observe: vi.fn(), disconnect: vi.fn() }
  const environment = {
    innerHeight: 800,
    matchMedia: vi.fn(() => media),
    ...(observerSupported ? { IntersectionObserver: class {
      constructor(handler) { callback = handler }
      observe(element) { observer.observe(element) }
      disconnect() { observer.disconnect() }
    } } : {}),
  }
  const element = { dataset: {}, getBoundingClientRect: () => ({ top }) }
  return { environment, element, media, observer, enter: () => callback([{ target: element, isIntersecting: true }]) }
}

describe('progressive motion accessibility and cleanup', () => {
  it.each([{ reduced: true }, { observerSupported: false }, { top: 100 }])('never hides first-screen or unsupported content: %j', options => {
    const fixture = revealFixture(options)
    const cleanup = startReveal(fixture.element, fixture.environment)
    expect(fixture.element.dataset.reveal).toBe('visible')
    cleanup()
  })

  it('reveals offscreen content once and disconnects the observer', () => {
    const fixture = revealFixture()
    const cleanup = startReveal(fixture.element, fixture.environment)
    expect(fixture.element.dataset.reveal).toBe('pending')
    fixture.enter()
    expect(fixture.element.dataset.reveal).toBe('visible')
    expect(fixture.observer.disconnect).toHaveBeenCalled()
    cleanup()
  })

  it('makes pending content visible when the motion preference changes', () => {
    const fixture = revealFixture()
    startReveal(fixture.element, fixture.environment)
    const handler = fixture.media.addEventListener.mock.calls[0][1]
    fixture.media.matches = true
    handler()
    expect(fixture.element.dataset.reveal).toBe('visible')
    expect(fixture.observer.disconnect).toHaveBeenCalled()
  })

  it('removes all listeners and restores visibility on unmount', () => {
    const fixture = revealFixture()
    const cleanup = startReveal(fixture.element, fixture.environment)
    cleanup()
    expect(fixture.media.removeEventListener).toHaveBeenCalled()
    expect(fixture.observer.disconnect).toHaveBeenCalled()
    expect(fixture.element.dataset.reveal).toBe('visible')
  })

  it('falls back to visible content if observer initialization fails', () => {
    const fixture = revealFixture()
    fixture.environment.IntersectionObserver = class { constructor() { throw new Error('observer unavailable') } }
    expect(() => startReveal(fixture.element, fixture.environment)).not.toThrow()
    expect(fixture.element.dataset.reveal).toBe('visible')
  })
})
