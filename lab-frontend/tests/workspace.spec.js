import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('@/utils/request', () => ({ default: vi.fn() }))
import request from '@/utils/request'
import { getWorkspace } from '@/api/workspace'
import { roleTitle, studentNavigation } from '@/utils/role-ui'
beforeEach(() => {
  request.mockReset()
  request.mockResolvedValue({ records: [], total: 0 })
})
describe('role workspace contracts', () => {
  it('student uses only personal records and available equipment', async () => {
    await getWorkspace('student')
    expect(request.mock.calls.map(([c]) => c.url).sort()).toEqual([
      '/equipment/page',
      '/repair/my',
      '/reservation/my',
    ])
    expect(request.mock.calls.find(([c]) => c.url === '/equipment/page')[0].params.status).toBe(0)
  })
  it.each(['admin', 'lab_admin'])('%s loads server-scoped management queues', async (role) => {
    await getWorkspace(role)
    expect(request.mock.calls.map(([c]) => c.url).sort()).toEqual([
      '/repair/page',
      '/reservation/page',
      '/stats/overview',
    ])
    for (const [config] of request.mock.calls.filter(([c]) => c.url.endsWith('/page'))) {
      expect(config.params).toEqual({ current: 1, size: 5, status: 0 })
      expect(config.params).not.toHaveProperty('adminId')
    }
  })
  it('one failed block does not erase other results or masquerade as zero', async () => {
    request.mockImplementation((c) =>
      c.url === '/reservation/my'
        ? Promise.reject(new Error('网络错误'))
        : Promise.resolve({ records: [], total: 7 }),
    )
    const result = await getWorkspace('student')
    expect(result.errors.reservations).toBe('网络错误')
    expect(result.data).not.toHaveProperty('reservations')
    expect(result.data.repairs.total).toBe(7)
  })
  it('malformed page data is not displayed as an empty list', async () => {
    request.mockResolvedValue({ total: 3 })
    const result = await getWorkspace('student')
    expect(Object.keys(result.errors)).toHaveLength(3)
    expect(Object.keys(result.data)).toHaveLength(0)
  })
  it('unknown role fails before any request', async () => {
    await expect(getWorkspace('guest')).rejects.toThrow('角色')
    expect(request).not.toHaveBeenCalled()
  })
  it('student navigation and headings do not expose manager entrances', () => {
    expect(
      studentNavigation.some((n) => ['/users', '/stats', '/reservation-review'].includes(n.path)),
    ).toBe(false)
    expect(roleTitle('/equipment', 'student')).toBe('找设备')
    expect(roleTitle('/home', 'admin')).toBe('运维工作台')
  })
})
