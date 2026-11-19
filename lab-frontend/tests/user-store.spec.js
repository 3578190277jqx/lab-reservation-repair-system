import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installStorage } from './helpers/storage'

const api = vi.hoisted(() => ({ login: vi.fn(), getUserInfo: vi.fn(), logout: vi.fn() }))
vi.mock('@/api/user', () => api)

let auth
let store

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const student = { id: 10, username: 'student10', realName: 'Student', role: 'student', status: 1 }
const administrator = { id: 1, username: 'admin', realName: 'Admin', role: 'admin', status: 1 }

beforeEach(async () => {
  vi.resetModules()
  vi.resetAllMocks()
  installStorage(vi)
  auth = await import('../src/utils/auth')
  const { createPinia, setActivePinia } = await import('pinia')
  setActivePinia(createPinia())
  const { useUserStore } = await import('../src/stores/user')
  store = useUserStore()
})

afterEach(() => vi.unstubAllGlobals())

describe('authoritative profile and session races', () => {
  it('deduplicates concurrent profile loads and reuses the successfully loaded profile', async () => {
    auth.saveSession({ token: 'student-token', expiresIn: 3600 })
    const pending = deferred()
    api.getUserInfo.mockReturnValue(pending.promise)
    const first = store.fetchUserInfo()
    const second = store.fetchUserInfo()
    expect(api.getUserInfo).toHaveBeenCalledTimes(1)
    pending.resolve(student)
    await expect(first).resolves.toEqual(student)
    await expect(second).resolves.toEqual(student)
    await expect(store.fetchUserInfo()).resolves.toEqual(student)
    expect(api.getUserInfo).toHaveBeenCalledTimes(1)
    expect(store.role).toBe('student')
  })

  it('uses authenticated /user/info for roles rather than the login response', async () => {
    api.login.mockResolvedValue({ token: 'student-token', expiresIn: 3600, role: 'admin', user: administrator })
    api.getUserInfo.mockResolvedValue(student)
    await store.signIn({ username: ' student10 ', password: ' password with spaces ' })
    expect(api.login).toHaveBeenCalledWith({ username: 'student10', password: ' password with spaces ' })
    expect(store.user).toEqual(student)
    expect(store.role).toBe('student')
    expect(store.isAdmin).toBe(false)
    expect(store.isManager).toBe(false)
  })

  it('does not let a previous session profile overwrite the next signed-in user', async () => {
    auth.saveSession({ token: 'old-admin-token', expiresIn: 3600 })
    const pending = deferred()
    api.getUserInfo.mockReturnValueOnce(pending.promise).mockResolvedValueOnce(student)
    const previousProfile = store.fetchUserInfo()
    const rejectedProfile = expect(previousProfile).rejects.toMatchObject({ code: 'ERR_AUTH_CHANGED' })
    api.login.mockResolvedValue({ token: 'new-student-token', expiresIn: 3600 })
    await store.signIn({ username: 'student10', password: 'password' })
    pending.resolve(administrator)
    await rejectedProfile
    expect(auth.getToken()).toBe('new-student-token')
    expect(store.user).toEqual(student)
    expect(store.role).toBe('student')
  })

  it('preserves the session after a profile network failure and allows retry', async () => {
    auth.saveSession({ token: 'student-token', expiresIn: 3600 })
    api.getUserInfo.mockRejectedValueOnce(new Error('Network unavailable')).mockResolvedValueOnce(student)
    await expect(store.fetchUserInfo()).rejects.toThrow('Network unavailable')
    expect(auth.getToken()).toBe('student-token')
    expect(store.user).toBeNull()
    await expect(store.fetchUserInfo()).resolves.toEqual(student)
    expect(api.getUserInfo).toHaveBeenCalledTimes(2)
  })

  it('keeps a newly issued token when the post-login profile request fails', async () => {
    api.login.mockResolvedValue({ token: 'new-token', expiresIn: 3600 })
    api.getUserInfo.mockRejectedValueOnce(new Error('Service unavailable')).mockResolvedValueOnce(student)
    await expect(store.signIn({ username: 'student10', password: 'password' })).rejects.toThrow('Service unavailable')
    expect(auth.getToken()).toBe('new-token')
    expect(store.loading).toBe(false)
    expect(store.user).toBeNull()
    await expect(store.fetchUserInfo()).resolves.toEqual(student)
    expect(store.role).toBe('student')
  })

  it('rejects disabled or unknown-role profiles without granting their permissions', async () => {
    auth.saveSession({ token: 'unverified-token', expiresIn: 3600 })
    api.getUserInfo.mockResolvedValueOnce({ ...administrator, status: 0 })
      .mockResolvedValueOnce({ ...student, role: 'superuser' })
    await expect(store.fetchUserInfo()).rejects.toMatchObject({ status: 403 })
    expect(store.user).toBeNull()
    expect(store.isAdmin).toBe(false)
    await expect(store.fetchUserInfo()).rejects.toMatchObject({ status: 403 })
    expect(store.role).toBe('')
  })

  it('clears local credentials on explicit logout even when server revocation is unavailable', async () => {
    auth.saveSession({ token: 'student-token', expiresIn: 3600 })
    api.getUserInfo.mockResolvedValue(student)
    await store.fetchUserInfo()
    api.logout.mockRejectedValue(new Error('Network unavailable'))
    await expect(store.logout()).resolves.toBe(false)
    expect(auth.getToken()).toBe('')
    expect(store.user).toBeNull()
    expect(store.role).toBe('')
  })

  it('does not let a delayed logout clear the next login', async () => {
    auth.saveSession({ token: 'old-token', expiresIn: 3600 })
    const pending = deferred()
    api.logout.mockReturnValue(pending.promise)
    const previousLogout = store.logout()
    api.login.mockResolvedValue({ token: 'new-token', expiresIn: 3600 })
    api.getUserInfo.mockResolvedValue(student)
    await store.signIn({ username: 'student10', password: 'password' })
    pending.resolve(null)
    await previousLogout
    expect(auth.getToken()).toBe('new-token')
    expect(store.user).toEqual(student)
  })
})
