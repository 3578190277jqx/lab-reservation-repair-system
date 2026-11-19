import { AxiosError, AxiosHeaders, CanceledError } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installStorage } from './helpers/storage'

const toast = vi.hoisted(() => {
  const message = vi.fn()
  message.error = vi.fn()
  message.warning = vi.fn()
  message.success = vi.fn()
  return message
})

vi.mock('element-plus', () => ({ ElMessage: toast }))

let request
let auth

beforeEach(async () => {
  vi.resetModules()
  vi.clearAllMocks()
  installStorage(vi)
  auth = await import('../src/utils/auth')
  request = (await import('../src/utils/request')).default
  auth.saveSession({ token: 'current-token', expiresIn: 3600 })
})

afterEach(() => vi.unstubAllGlobals())

function response(config, data, status = 200) {
  return { config, data, status, statusText: String(status), headers: new AxiosHeaders(), request: {} }
}

function failure(config, data, status) {
  return new AxiosError(`HTTP ${status}`, AxiosError.ERR_BAD_REQUEST, config, {}, response(config, data, status))
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function expectNoToast() {
  expect(toast).not.toHaveBeenCalled()
  expect(toast.error).not.toHaveBeenCalled()
  expect(toast.warning).not.toHaveBeenCalled()
}

describe('HTTP boundary and session safety', () => {
  it('uses /api, sends the current Bearer token, unwraps Result.data and honors skipAuth', async () => {
    expect(request.defaults.baseURL).toBe('/api')
    request.defaults.adapter = async (config) => {
      expect(config.headers.get('Authorization')).toBe('Bearer current-token')
      return response(config, { code: 200, message: 'OK', data: { id: 1, name: 'equipment' } })
    }
    await expect(request.get('/equipment/1')).resolves.toEqual({ id: 1, name: 'equipment' })
    request.defaults.adapter = async (config) => {
      expect(config.headers.get('Authorization')).toBeUndefined()
      return response(config, { code: 200, data: { public: true } })
    }
    await expect(request.post('/user/login', {}, {
      skipAuth: true,
      headers: { Authorization: 'Bearer must-not-leak' },
    })).resolves.toEqual({ public: true })
  })

  it('expires a current 401 but preserves a new login when an older request fails later', async () => {
    const expired = vi.fn()
    auth.onAuthExpired(expired)
    const started = deferred()
    const delivery = deferred()
    request.defaults.adapter = (config) => { started.resolve(config); return delivery.promise }
    const oldRequest = request.get('/user/info')
    const oldConfig = await started.promise
    const expectedFailure = expect(oldRequest).rejects.toBeTruthy()
    auth.saveSession({ token: 'new-token', expiresIn: 3600 })
    delivery.reject(failure(oldConfig, { code: 401, message: 'Expired old request' }, 401))
    await expectedFailure
    expect(auth.getToken()).toBe('new-token')
    expect(expired).not.toHaveBeenCalled()

    request.defaults.adapter = async (config) => { throw failure(config, { code: 401, message: 'Current session expired' }, 401) }
    await expect(request.get('/user/info')).rejects.toBeTruthy()
    expect(auth.getToken()).toBe('')
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('login errors, forbidden responses, conflicts and network errors keep an existing session', async () => {
    request.defaults.adapter = async (config) => { throw failure(config, { code: 401, message: 'Wrong password' }, 401) }
    await expect(request.post('/user/login', {}, { skipAuth: true })).rejects.toBeTruthy()
    expect(auth.getToken()).toBe('current-token')
    for (const status of [403, 409]) {
      request.defaults.adapter = async (config) => { throw failure(config, { code: status, message: 'Rejected operation' }, status) }
      await expect(request.post('/repair/accept')).rejects.toBeTruthy()
      expect(auth.getToken()).toBe('current-token')
    }
    request.defaults.adapter = async (config) => { throw new AxiosError('Network unavailable', AxiosError.ERR_NETWORK, config) }
    await expect(request.get('/equipment/page')).rejects.toBeTruthy()
    expect(auth.getToken()).toBe('current-token')
    expect(toast.mock.calls.length + toast.error.mock.calls.length).toBeGreaterThan(0)
  })

  it('rejects business errors even on HTTP 200 without returning a fake successful result', async () => {
    request.defaults.adapter = async (config) => response(config, { code: 40902, message: 'Invalid state transition', data: null })
    await expect(request.post('/repair/complete')).rejects.toBeTruthy()
    expect(auth.getToken()).toBe('current-token')
    expect(toast.mock.calls.length + toast.error.mock.calls.length).toBeGreaterThan(0)
  })

  it('handles a business 401 on HTTP 200 as expiration and notifies once for concurrent failures', async () => {
    const expired = vi.fn()
    auth.onAuthExpired(expired)
    const started = []
    const delivered = []
    request.defaults.adapter = (config) => {
      const delivery = deferred()
      started.push(config)
      delivered.push(delivery)
      return delivery.promise
    }
    const requests = [request.get('/user/info'), request.get('/equipment/page')]
    const failures = requests.map((promise) => expect(promise).rejects.toBeTruthy())
    await vi.waitFor(() => expect(started).toHaveLength(2))
    delivered.forEach((delivery, index) => {
      delivery.resolve(response(started[index], { code: 401, message: 'Login expired', data: null }))
    })
    await Promise.all(failures)
    expect(auth.getToken()).toBe('')
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('preserves the current session when an old request returns a business 401', async () => {
    const started = deferred()
    const delivery = deferred()
    request.defaults.adapter = (config) => { started.resolve(config); return delivery.promise }
    const pending = request.get('/equipment/page')
    const config = await started.promise
    const failureExpected = expect(pending).rejects.toBeTruthy()
    auth.saveSession({ token: 'replacement-token', expiresIn: 3600 })
    delivery.resolve(response(config, { code: 401, message: 'Old session expired' }))
    await failureExpected
    expect(auth.getToken()).toBe('replacement-token')
  })

  it('cancellation causes neither a toast nor a logout', async () => {
    request.defaults.adapter = async (config) => { throw new CanceledError('Page changed', config) }
    await expect(request.get('/equipment/page')).rejects.toBeTruthy()
    expect(auth.getToken()).toBe('current-token')
    expectNoToast()
  })

  it('returns image Blobs and parses JSON Blob failures with their backend message and auth status', async () => {
    const image = new Blob([new Uint8Array([137, 80, 78, 71])], { type: 'image/png' })
    request.defaults.adapter = async (config) => response(config, image)
    await expect(request.get('/file/images/3/a.png', { responseType: 'blob' })).resolves.toBe(image)

    const bad = new Blob([JSON.stringify({ code: 400, message: 'Image is not available', data: null })], { type: 'application/json' })
    request.defaults.adapter = async (config) => { throw failure(config, bad, 400) }
    await expect(request.get('/file/images/3/a.png', { responseType: 'blob' })).rejects.toThrow('Image is not available')
    expect(auth.getToken()).toBe('current-token')

    const expired = new Blob([JSON.stringify({ code: 401, message: 'Login required', data: null })], { type: 'application/json' })
    request.defaults.adapter = async (config) => { throw failure(config, expired, 401) }
    await expect(request.get('/file/images/3/a.png', { responseType: 'blob' })).rejects.toThrow('Login required')
    expect(auth.getToken()).toBe('')
  })
})
