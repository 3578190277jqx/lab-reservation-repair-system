import { describe, expect, it, vi } from 'vitest'
import { studentNavigation } from '@/utils/role-ui'

vi.mock('vue-router', async (importOriginal) => {
  const original = await importOriginal()
  return { ...original, createWebHistory: () => original.createMemoryHistory() }
})

import { routes } from '@/router'

describe('fault notification navigation', () => {
  const children = routes.find((route) => route.path === '/').children

  it('exposes a student notification route and a discoverable navigation entry', () => {
    expect(studentNavigation.some((item) => item.path === '/notifications')).toBe(true)
    expect(children.find((item) => item.path === 'notifications')?.meta.roles).toContain('student')
  })

  it('limits the manual contact route to managers', () => {
    expect(children.find((item) => item.path === 'contact-todos')?.meta.roles).toEqual([
      'admin',
      'lab_admin',
    ])
  })
})
