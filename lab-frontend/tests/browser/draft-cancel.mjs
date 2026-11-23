// Run against the isolated workspace fixture server, never a live business API.
// PLAYWRIGHT_PACKAGE_JSON may point to an existing bundled Playwright installation.
import assert from 'node:assert/strict'
import process from 'node:process'
import console from 'node:console'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const require = createRequire(process.env.PLAYWRIGHT_PACKAGE_JSON || import.meta.url)
const { chromium } = require('playwright')
const base = 'http://127.0.0.1:5198'
const output = path.resolve(process.env.DRAFT_CHECK_OUTPUT || 'output/draft-cancel')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const results = []
const scenarios = ['continue', 'discard', 'empty', 'failed-submit', 'pending-submit']

for (const width of [1366, 390]) {
  for (const scenario of scenarios) {
    const context = await browser.newContext({
      viewport: { width, height: width === 390 ? 844 : 768 },
      timezoneId: 'Asia/Shanghai',
    })
    const page = await context.newPage()
    page.setDefaultTimeout(4000)
    const name = `${width}-${scenario}`
    let release
    try {
      const reset = await page.request.get(`${base}/__fixture/reset`)
      assert.equal(reset.status(), 200, 'isolated fixture must be available')
      await page.goto(`${base}/equipment?role=student`)
      await page.getByRole('button', { name: '查看数字示波器排期', exact: true }).click()
      await page.getByRole('button', { name: '申请这台设备', exact: true }).click()
      const form = page.getByRole('region', { name: '申请预约', exact: true })
      const purpose = page.getByRole('textbox', { name: '预约用途', exact: true })
      const start = page.getByRole('combobox', { name: '预约开始时间（北京时间）' })
      const end = page.getByRole('combobox', { name: '预约结束时间（北京时间）' })
      const cancel = form.getByRole('button', { name: '取消', exact: true })
      if (scenario !== 'empty') {
        await form.getByRole('button', { name: '选择明天 09:00–10:00' }).click()
        await purpose.fill('草稿保护回归：示波器信号实验')
      }
      const original = {
        purpose: await purpose.inputValue(),
        start: await start.inputValue(),
        end: await end.inputValue(),
      }
      if (scenario === 'pending-submit') {
        const gate = new Promise((resolve) => {
          release = resolve
        })
        await page.route('**/api/reservation/apply', async (route) => {
          await gate
          await route.continue()
        })
        const requested = page.waitForRequest('**/api/reservation/apply')
        await form.getByRole('button', { name: '提交', exact: true }).click()
        await requested
        assert.equal(await cancel.isDisabled(), true, 'cannot cancel during submission')
        assert.equal(await purpose.isDisabled(), true)
        assert.equal(await page.getByRole('dialog', { name: '放弃预约草稿？' }).count(), 0)
        release()
        await form.getByRole('alert').filter({ hasText: '所选时段已被占用' }).waitFor()
      }
      if (scenario === 'failed-submit') {
        await form.getByRole('button', { name: '提交', exact: true }).click()
        await form.getByRole('alert').filter({ hasText: '所选时段已被占用' }).waitFor()
      }
      await cancel.click()
      if (scenario === 'empty') {
        await form.waitFor({ state: 'hidden' })
        assert.equal(await page.getByRole('dialog', { name: '放弃预约草稿？' }).count(), 0)
      } else {
        const confirmation = page.getByRole('dialog', { name: '放弃预约草稿？' })
        await confirmation.waitFor()
        assert.equal(await confirmation.count(), 1)
        // Wait for the dialog's enter transition before capturing visual evidence.
        await confirmation
          .getByRole('button', { name: '继续填写', exact: true })
          .click({ trial: true })
        await page.screenshot({
          path: path.join(output, `${name}-confirm.png`),
          animations: 'disabled',
        })
        await confirmation
          .getByRole('button', {
            name: scenario === 'discard' ? '放弃草稿' : '继续填写',
            exact: true,
          })
          .click()
        await confirmation.waitFor({ state: 'hidden' })
        if (scenario === 'discard') {
          await form.waitFor({ state: 'hidden' })
          await page.getByRole('button', { name: '申请这台设备', exact: true }).click()
          assert.equal(await purpose.inputValue(), '')
          assert.equal(await start.inputValue(), '')
          assert.equal(await end.inputValue(), '')
        } else {
          assert.equal(await form.isVisible(), true)
          assert.deepEqual(
            {
              purpose: await purpose.inputValue(),
              start: await start.inputValue(),
              end: await end.inputValue(),
            },
            original,
            'continue must preserve all draft fields',
          )
          if (scenario.includes('submit')) {
            await form.getByRole('alert').filter({ hasText: '所选时段已被占用' }).waitFor()
          }
        }
      }
      await page.locator('.planning-content h2').filter({ hasText: '数字示波器' }).waitFor()
      const journal = await (await page.request.get(`${base}/__fixture/journal`)).json()
      assert.equal(
        journal.data.filter((r) => r.path === '/reservation/apply').length,
        scenario.includes('submit') ? 1 : 0,
        'cancel must never submit a booking',
      )
      assert.equal(
        await page.evaluate(
          () => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth,
        ),
        true,
      )
      if (await form.isVisible()) await purpose.scrollIntoViewIfNeeded()
      await page.screenshot({ path: path.join(output, `${name}-result.png`) })
      results.push({ name, passed: true, api: 'synthetic fixture only' })
    } catch (error) {
      await page.screenshot({ path: path.join(output, `${name}-failure.png`) }).catch(() => {})
      results.push({ name, passed: false, error: error.message })
    } finally {
      release?.()
      await context.close()
    }
    console.log(
      `${results.at(-1).passed ? 'PASS' : 'FAIL'} ${name}: ${results.at(-1).error || 'ok'}`,
    )
  }
}
await browser.close()
await writeFile(path.join(output, 'results.json'), JSON.stringify(results, null, 2))
process.exitCode = results.some((r) => !r.passed) ? 1 : 0
