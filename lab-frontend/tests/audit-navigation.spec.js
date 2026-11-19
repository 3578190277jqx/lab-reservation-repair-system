import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installStorage } from './helpers/storage'
const mocks = vi.hoisted(() => ({ getUserInfo: vi.fn() }))
vi.mock('@/api/user', () => ({ getUserInfo: mocks.getUserInfo, login: vi.fn(), logout: vi.fn() }))
// 本组验证真实路由守卫，不挂载UI；界面与请求行为由其他测试和浏览器验证覆盖。
vi.mock('@/layout/index.vue', () => ({ default: { template: '<router-view />' } }))
vi.mock('@/views/OperationLogView.vue', () => ({ default: { template: '<div />' } }))
vi.mock('vue-router', async (importOriginal) => {
  const original = await importOriginal()
  return { ...original, createWebHistory: () => original.createMemoryHistory() }
})
let router, auth
beforeEach(async () => {
  vi.resetModules()
  vi.clearAllMocks()
  installStorage(vi)
  auth = await import('@/utils/auth')
  router = (await import('@/router')).default
  vi.stubGlobal('document', { title: '' })
})
afterEach(() => vi.unstubAllGlobals())
describe('audit navigation permission boundary', () => {
  it('redirects an unauthenticated audit URL to login', async () => {
    await router.push('/operation-logs')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/operation-logs')
  })
  it.each(['student', 'lab_admin'])('rejects direct audit navigation by %s', async (role) => {
    auth.saveSession({ token: role, expiresIn: 3600 })
    mocks.getUserInfo.mockResolvedValue({ id: 1, role, status: 1 })
    await router.push('/operation-logs')
    expect(router.currentRoute.value.name).toBe('forbidden')
  })
  it('allows admin through the actual route guard', async () => {
    auth.saveSession({ token: 'admin', expiresIn: 3600 })
    mocks.getUserInfo.mockResolvedValue({ id: 1, role: 'admin', status: 1 })
    await router.push('/operation-logs')
    expect(router.currentRoute.value.name).toBe('operation-logs')
    expect(router.currentRoute.value.meta.roles).toEqual(['admin'])
  })
})
