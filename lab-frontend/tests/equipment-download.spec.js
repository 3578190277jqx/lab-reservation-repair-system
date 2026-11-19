import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { AxiosHeaders } from 'axios'
import { installStorage } from './helpers/storage'
const toast = vi.hoisted(() => ({ error: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: toast }))
let request, auth, equipment
beforeEach(async () => {
  vi.resetModules()
  installStorage(vi)
  vi.clearAllMocks()
  auth = await import('@/utils/auth')
  auth.saveSession({ token: 'excel-token', expiresIn: 3600 })
  request = (await import('@/utils/request')).default
  equipment = await import('@/api/equipment')
})
afterEach(() => vi.unstubAllGlobals())
function deliver(config, data) {
  return { config, data, status: 200, statusText: '200', headers: new AxiosHeaders(), request: {} }
}
describe('Excel binary HTTP contract', () => {
  it('downloads a ZIP-based xlsx with current Bearer authorization', async () => {
    const blob = new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04, 1])], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    request.defaults.adapter = async (config) => {
      expect(config.url).toBe('/equipment/export')
      expect(config.params).toEqual({ name: '光学平台' })
      expect(config.headers.get('Authorization')).toBe('Bearer excel-token')
      expect(config.responseType).toBe('blob')
      return deliver(config, blob)
    }
    await expect(equipment.exportEquipment({ name: '光学平台' })).resolves.toBe(blob)
  })
  it.each(['application/json', 'application/octet-stream'])(
    'does not download a JSON error disguised as %s',
    async (type) => {
      request.defaults.adapter = async (config) =>
        deliver(
          config,
          new Blob([JSON.stringify({ code: 403, message: '无权导出该实验室' })], { type }),
        )
      await expect(equipment.exportEquipment({})).rejects.toMatchObject({
        code: 403,
        message: '无权导出该实验室',
      })
      expect(auth.getToken()).toBe('excel-token')
    },
  )
  it('expires the current session when octet-stream contains a 401 JSON error', async () => {
    request.defaults.adapter = async (config) =>
      deliver(
        config,
        new Blob([JSON.stringify({ code: 401, message: '登录已失效' })], {
          type: 'application/octet-stream',
        }),
      )
    await expect(equipment.getEquipmentImportTemplate()).rejects.toMatchObject({ code: 401 })
    expect(auth.getToken()).toBe('')
  })
  it('rejects HTML/proxy responses rather than downloading them as xlsx', async () => {
    request.defaults.adapter = async (config) =>
      deliver(config, new Blob(['<html>Error</html>'], { type: 'text/html' }))
    await expect(equipment.exportEquipment({})).rejects.toThrow('Excel')
  })
  it('uses the authenticated multipart file field without forcing a content-type boundary', async () => {
    const file = new File(['xlsx'], '设备.xlsx')
    request.defaults.adapter = async (config) => {
      expect(config.url).toBe('/equipment/import')
      expect(config.method).toBe('post')
      expect(config.headers.get('Authorization')).toBe('Bearer excel-token')
      expect(config.data).toBeInstanceOf(FormData)
      expect(config.data.get('file').name).toBe('设备.xlsx')
      expect(config.timeout).toBe(60000)
      return deliver(config, { code: 200, data: { importedCount: 1 } })
    }
    await expect(equipment.importEquipment(file)).resolves.toEqual({ importedCount: 1 })
  })
})
