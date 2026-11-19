import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installStorage } from './helpers/storage'

let auth
let storage

beforeEach(async () => {
  vi.resetModules()
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2030-01-01T00:00:00Z'))
  storage = installStorage(vi)
  auth = await import('../src/utils/auth')
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('session lifecycle', () => {
  it('stores the token only for its lifetime, with no profile or role persistence', () => {
    expect(auth.getToken()).toBe('')
    auth.saveSession({ token: 'current-token', expiresIn: 60, role: 'admin', profile: { name: 'private' } })
    expect(auth.getToken()).toBe('current-token')
    expect(storage.localStorage.length).toBe(0)
    const persisted = [...storage.sessionStorage.values.values()].join(' ')
    expect(persisted).toContain('current-token')
    expect(persisted).not.toContain('admin')
    expect(persisted).not.toContain('private')
    vi.advanceTimersByTime(59_000)
    expect(auth.getToken()).toBe('current-token')
    vi.advanceTimersByTime(1_001)
    expect(auth.getToken()).toBe('')
  })

  it('clears and notifies only when the failing request belongs to the current session', () => {
    const expired = vi.fn()
    const unsubscribe = auth.onAuthExpired(expired)
    auth.saveSession({ token: 'new-token', expiresIn: 3600 })
    auth.expireSession('old-token')
    expect(auth.getToken()).toBe('new-token')
    expect(expired).not.toHaveBeenCalled()
    auth.expireSession('new-token')
    expect(auth.getToken()).toBe('')
    expect(expired).toHaveBeenCalledTimes(1)
    unsubscribe()
    auth.saveSession({ token: 'another-token', expiresIn: 3600 })
    auth.expireSession('another-token')
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('notifies on a current request 401 even if its local lifetime elapsed while waiting', () => {
    const expired = vi.fn()
    auth.onAuthExpired(expired)
    auth.saveSession({ token: 'in-flight-token', expiresIn: 1 })
    const requestToken = auth.getToken()
    vi.advanceTimersByTime(1_001)
    auth.expireSession(requestToken)
    expect(auth.getToken()).toBe('')
    expect(expired).toHaveBeenCalledTimes(1)
    auth.expireSession(requestToken)
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('notifies once when the next token read discovers a naturally expired session', async () => {
    const expired = vi.fn()
    auth.onAuthExpired(expired)
    auth.saveSession({ token: 'idle-token', expiresIn: 1 })
    vi.advanceTimersByTime(1_001)
    expect(auth.getToken()).toBe('')
    expect(auth.getToken()).toBe('')
    await Promise.resolve()
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('suppresses a queued local-expiration notification after a new session has started', async () => {
    const expired = vi.fn()
    auth.onAuthExpired(expired)
    auth.saveSession({ token: 'idle-token', expiresIn: 1 })
    vi.advanceTimersByTime(1_001)
    expect(auth.getToken()).toBe('')
    auth.saveSession({ token: 'fresh-token', expiresIn: 3600 })
    await Promise.resolve()
    expect(auth.getToken()).toBe('fresh-token')
    expect(expired).not.toHaveBeenCalled()
  })

  it('manual clearing removes the session and does not leave a token after reloading the module', async () => {
    auth.saveSession({ token: 'logout-token', expiresIn: 3600 })
    auth.clearSession()
    expect(auth.getToken()).toBe('')
    vi.resetModules()
    const reloaded = await import('../src/utils/auth')
    expect(reloaded.getToken()).toBe('')
  })

  it('rejects invalid login credentials before replacing a valid session', () => {
    auth.saveSession({ token: 'valid-token', expiresIn: 60 })
    for (const invalid of [
      { token: '', expiresIn: 60 },
      { token: '  ', expiresIn: 60 },
      { token: 'bad-expiry', expiresIn: 0 },
      { token: 'bad-expiry', expiresIn: -1 },
      { token: 'bad-expiry', expiresIn: NaN },
      { token: 'bad-expiry', expiresIn: Infinity },
    ]) {
      expect(() => auth.saveSession(invalid)).toThrow()
      expect(auth.getToken()).toBe('valid-token')
    }
  })

  it('still supports login and logout when browser storage is unavailable', () => {
    const unavailable = {
      getItem() { throw new Error('Storage access denied') },
      setItem() { throw new Error('Storage access denied') },
      removeItem() { throw new Error('Storage access denied') },
    }
    vi.stubGlobal('sessionStorage', unavailable)
    storage.window.sessionStorage = unavailable
    auth.saveSession({ token: 'memory-token', expiresIn: 60 })
    expect(auth.getToken()).toBe('memory-token')
    auth.clearSession()
    expect(auth.getToken()).toBe('')
  })
})
