import { describe, expect, it } from 'vitest'
import { safeRedirect } from '../src/utils/navigation'

describe('safe post-login redirect', () => {
  it('preserves internal path, query and fragment', () => {
    for (const path of ['/dashboard', '/equipment?page=2#detail', '/reservation/my?status=1']) {
      expect(safeRedirect(path)).toBe(path)
    }
  })

  it('rejects external addresses, browser path ambiguities, control characters and repeated query values', () => {
    for (const value of [
      'https://evil.example/path', 'http://evil.example', '//evil.example', '/\\evil.example',
      '\\evil.example', 'javascript:alert(1)', 'equipment', '', null, undefined,
      '/equipment\nLocation: https://evil.example', '/equipment\u0000', ['/dashboard', '//evil.example'],
      '/%2f/evil.example', '/%5cevil.example', '/equipment%0aLocation:evil', '/equipment%ZZ',
    ]) {
      expect(safeRedirect(value), String(value)).toBe('')
    }
  })

  it('rejects redirects back to authentication pages', () => {
    for (const value of [
      '/login', '/login?redirect=/dashboard', '/register', '/register#form',
      '/session-error', '/session-error?redirect=/dashboard',
      '/LOGIN/', '/%6cogin', '/register/',
    ]) {
      expect(safeRedirect(value), value).toBe('')
    }
  })
})
