/** 兼容当前公网HTTP演示；复制失败时不假报成功。只能由用户点击触发。 */
export async function copyText(text) {
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    try { await navigator.clipboard.writeText(text); return } catch { /* 回退到兼容复制 */ }
  }
  const previous = document.activeElement
  const field = document.createElement('textarea')
  field.value = text; field.readOnly = true; field.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0'
  document.body.appendChild(field)
  try {
    field.select()
    if (!document.execCommand('copy')) throw new Error('浏览器未允许复制，请选中回答手动复制')
  } finally { field.remove(); previous?.focus({ preventScroll: true }) }
}
