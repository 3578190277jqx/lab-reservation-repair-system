import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const folder = new URL('../src/components/workflow/', import.meta.url)
const parent = readFileSync(
  new URL('../src/components/WorkflowDialog.vue', import.meta.url),
  'utf8',
)

describe('workflow view boundaries', () => {
  it.each(['ReservationActionForm', 'RepairActionForm', 'RepairDetails'])(
    'delegates %s to a child sharing the parent controller',
    (name) => {
      const file = new URL(`${name}.vue`, folder)
      expect(existsSync(fileURLToPath(file))).toBe(true)
      const child = readFileSync(file, 'utf8')
      expect(parent).toContain(`<${name}`)
      expect(parent).toMatch(new RegExp(`<${name}[^>]*:controller="controller"`))
      expect(child).not.toContain('useWorkflowDialog(')
    },
  )
})
