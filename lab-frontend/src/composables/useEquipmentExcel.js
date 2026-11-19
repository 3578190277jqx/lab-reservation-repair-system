import { computed, shallowRef, toValue } from 'vue'
import { exportEquipment, getEquipmentImportTemplate, importEquipment } from '@/api/equipment'
import { getToken } from '@/utils/auth'
import { saveDownload } from '@/utils/download'

/** The role gate improves UI; backend separately enforces role and lab scope. */
export function useEquipmentExcel({
  resource,
  role,
  getFilters = () => ({}),
  onImported = () => {},
  download = saveDownload,
}) {
  const visible = computed(
    () => toValue(resource) === 'equipment' && ['admin', 'lab_admin'].includes(toValue(role)),
  )
  const busy = shallowRef('')
  const error = shallowRef('')
  const success = shallowRef('')
  async function run(operation, callback) {
    if (!visible.value || busy.value) return
    const token = getToken()
    busy.value = operation
    error.value = ''
    success.value = ''
    const current = () => visible.value && token === getToken()
    try {
      await callback(current)
    } catch (err) {
      if (current()) error.value = err.message || '操作失败，请稍后重试'
    } finally {
      busy.value = ''
    }
  }
  function exportRows() {
    return run('export', async (current) => {
      const file = await exportEquipment(getFilters())
      if (current()) download(file, '实验室设备.xlsx')
    })
  }
  function downloadTemplate() {
    return run('template', async (current) => {
      const file = await getEquipmentImportTemplate()
      if (current()) download(file, '设备导入模板.xlsx')
    })
  }
  function uploadFile(file) {
    return run('import', async (current) => {
      if (!file || !/\.xlsx$/i.test(file.name)) throw new Error('请选择 .xlsx 格式的 Excel 文件')
      if (file.size > 2 * 1024 * 1024) throw new Error('文件不能超过 2 MiB，每次最多导入 500 行')
      const result = await importEquipment(file)
      if (!current()) return
      if (!Number.isInteger(result?.importedCount) || result.importedCount < 1)
        throw new Error('导入结果格式异常，请刷新列表核对')
      success.value = `成功新增 ${result.importedCount} 台设备`
      await onImported(result)
    })
  }
  return { visible, busy, error, success, exportRows, downloadTemplate, uploadFile }
}
