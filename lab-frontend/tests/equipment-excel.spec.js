import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSSRApp, shallowRef } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { deferred } from './helpers/composable'
const mocks = vi.hoisted(() => ({
  exportRows: vi.fn(),
  template: vi.fn(),
  importFile: vi.fn(),
  token: 'token-a',
}))
vi.mock('@/api/equipment', () => ({
  exportEquipment: mocks.exportRows,
  getEquipmentImportTemplate: mocks.template,
  importEquipment: mocks.importFile,
}))
vi.mock('@/utils/auth', () => ({ getToken: () => mocks.token }))
import { useEquipmentExcel } from '@/composables/useEquipmentExcel'
import EquipmentExcelActions from '@/components/resources/EquipmentExcelActions.vue'
beforeEach(() => {
  vi.clearAllMocks()
  mocks.token = 'token-a'
  mocks.exportRows.mockResolvedValue(new Blob(['excel']))
  mocks.template.mockResolvedValue(new Blob(['template']))
  mocks.importFile.mockResolvedValue({ importedCount: 2 })
})
function fixture(resource = 'equipment', role = 'admin') {
  const download = vi.fn(),
    onImported = vi.fn()
  return {
    download,
    onImported,
    ...useEquipmentExcel({
      resource,
      role,
      download,
      onImported,
      getFilters: () => ({ name: '示波器', status: 0 }),
    }),
  }
}
const file = () => new File(['xlsx'], '设备.xlsx')
describe('equipment Excel actions', () => {
  it.each(['admin', 'lab_admin'])(
    '%s exports the applied filters and downloads the empty template',
    async (role) => {
      const f = fixture('equipment', role)
      expect(f.visible.value).toBe(true)
      await f.exportRows()
      await f.downloadTemplate()
      expect(mocks.exportRows).toHaveBeenCalledWith({ name: '示波器', status: 0 })
      expect(mocks.template).toHaveBeenCalledOnce()
      expect(f.download.mock.calls.map((call) => call[1])).toEqual([
        '实验室设备.xlsx',
        '设备导入模板.xlsx',
      ])
    },
  )
  it.each(['lab', 'reservation', 'repair', 'user'])(
    'does not render or request Excel for resource %s',
    async (resource) => {
      const f = fixture(resource)
      await f.exportRows()
      await f.uploadFile(file())
      const html = await renderToString(
        createSSRApp(EquipmentExcelActions, { resource, role: 'admin' }),
      )
      expect(html).not.toContain('导入设备')
      expect(mocks.exportRows).not.toHaveBeenCalled()
      expect(mocks.importFile).not.toHaveBeenCalled()
    },
  )
  it.each(['student', 'guest', ''])('does not expose Excel for role %s', async (role) => {
    const f = fixture('equipment', role)
    await f.downloadTemplate()
    await f.uploadFile(file())
    expect(f.visible.value).toBe(false)
    const html = await renderToString(
      createSSRApp(EquipmentExcelActions, { resource: 'equipment', role }),
    )
    expect(html).not.toContain('导入设备')
    expect(mocks.template).not.toHaveBeenCalled()
    expect(mocks.importFile).not.toHaveBeenCalled()
  })
  it('refreshes the list exactly once after successful import', async () => {
    const f = fixture(),
      input = file()
    await f.uploadFile(input)
    expect(mocks.importFile).toHaveBeenCalledWith(input)
    expect(f.onImported).toHaveBeenCalledExactlyOnceWith({ importedCount: 2 })
    expect(f.success.value).toContain('2')
  })
  it('does not refresh on failed import and preserves server validation detail', async () => {
    mocks.importFile.mockRejectedValue(new Error('第 3 行设备编号重复'))
    const f = fixture()
    await f.uploadFile(file())
    expect(f.onImported).not.toHaveBeenCalled()
    expect(f.error.value).toContain('第 3 行')
  })
  it('rejects oversized and wrong-extension files before requesting', async () => {
    const f = fixture()
    await f.uploadFile(new File(['x'], '设备.csv'))
    expect(f.error.value).toContain('.xlsx')
    await f.uploadFile({ name: '设备.xlsx', size: 2 * 1024 * 1024 + 1 })
    expect(f.error.value).toContain('2 MiB')
    expect(mocks.importFile).not.toHaveBeenCalled()
  })
  it('prevents duplicate imports and ignores completion after a session change', async () => {
    const delivery = deferred()
    mocks.importFile.mockReturnValue(delivery.promise)
    const f = fixture()
    const pending = f.uploadFile(file())
    await f.uploadFile(file())
    expect(mocks.importFile).toHaveBeenCalledOnce()
    mocks.token = 'token-b'
    delivery.resolve({ importedCount: 2 })
    await pending
    expect(f.onImported).not.toHaveBeenCalled()
    expect(f.success.value).toBe('')
  })
  it('does not download a previous resource export after navigation', async () => {
    const delivery = deferred()
    mocks.exportRows.mockReturnValue(delivery.promise)
    const resource = shallowRef('equipment'),
      f = fixture(resource)
    const pending = f.exportRows()
    resource.value = 'lab'
    delivery.resolve(new Blob(['old']))
    await pending
    expect(f.download).not.toHaveBeenCalled()
  })
  it('rejects malformed import counts instead of reporting false success', async () => {
    mocks.importFile.mockResolvedValue({ importedCount: '2' })
    const f = fixture()
    await f.uploadFile(file())
    expect(f.onImported).not.toHaveBeenCalled()
    expect(f.error.value).toContain('格式异常')
  })
})
