// Run only against the synthetic fixture server on localhost:5198.
import assert from 'node:assert/strict'
import process from 'node:process'
import console from 'node:console'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const require = createRequire(process.env.PLAYWRIGHT_PACKAGE_JSON || import.meta.url)
const { chromium } = require('playwright')
const base = 'http://127.0.0.1:5198'
const output = path.resolve(process.env.DRAFT_ROUTE_OUTPUT || 'output/draft-route')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const results = []
for (const width of [1366, 390]) {
  for (const scenario of [
    'link-continue',
    'link-discard',
    'back-continue',
    'back-discard',
    'empty-back',
    'expired-back',
    'changed-back',
    'confirm-expire',
    'confirm-changed',
    'error-route',
    'pending-back',
  ]) {
    if (
      width === 390 &&
      (scenario.startsWith('link') || scenario.startsWith('confirm') || scenario === 'error-route')
    )
      continue // mobile drawer blocks the header navigation
    const context = await browser.newContext({
      viewport: { width, height: width === 390 ? 844 : 768 },
      timezoneId: 'Asia/Shanghai',
    })
    const page = await context.newPage()
    page.setDefaultTimeout(3500)
    const name = `${width}-${scenario}`
    let release
    try {
      assert.equal((await page.request.get(`${base}/__fixture/reset`)).status(), 200)
      await page.goto(`${base}/home?role=student`)
      await page.getByRole('link', { name: '找设备', exact: true }).click()
      await page.getByRole('button', { name: '查看数字示波器排期', exact: true }).click()
      await page.getByRole('button', { name: '申请这台设备', exact: true }).click()
      const form = page.getByRole('region', { name: '申请预约', exact: true })
      const purpose = page.getByRole('textbox', { name: '预约用途', exact: true })
      const start = page.getByRole('combobox', { name: '预约开始时间（北京时间）' })
      const end = page.getByRole('combobox', { name: '预约结束时间（北京时间）' })
      if (scenario !== 'empty-back') {
        await form.getByRole('button', { name: '选择明天 09:00–10:00' }).click()
        await purpose.fill('路由保护回归实验')
      }
      const original = [
        await purpose.inputValue(),
        await start.inputValue(),
        await end.inputValue(),
      ]
      if (scenario === 'expired-back') {
        await page.evaluate(() =>
          globalThis.sessionStorage.setItem(
            'lab.auth.session',
            JSON.stringify({ token: 'fixture-student', expiresAt: Date.now() - 1000 }),
          ),
        )
      }
      if (scenario === 'changed-back') {
        await page.evaluate(() =>
          globalThis.sessionStorage.setItem(
            'lab.auth.session',
            JSON.stringify({ token: 'fixture-admin', expiresAt: Date.now() + 3600000 }),
          ),
        )
      }
      if (scenario === 'pending-back') {
        const gate = new Promise((resolve) => {
          release = resolve
        })
        await page.route('**/api/reservation/apply', async (route) => {
          await gate
          await route.continue()
        })
        const request = page.waitForRequest('**/api/reservation/apply')
        await form.getByRole('button', { name: '提交', exact: true }).click()
        await request
      }
      if (scenario.startsWith('confirm-')) {
        await page.getByRole('link', { name: '我的预约', exact: true }).click()
        const prompt = page.getByRole('dialog', { name: '放弃预约草稿？' })
        await prompt.waitFor()
        await page.evaluate(
          (changed) =>
            globalThis.sessionStorage.setItem(
              'lab.auth.session',
              JSON.stringify({
                token: changed ? 'fixture-admin' : 'fixture-student',
                expiresAt: Date.now() + (changed ? 3600000 : -1000),
              }),
            ),
          scenario === 'confirm-changed',
        )
        await page.goBack({ waitUntil: 'commit', timeout: 1500 }).catch(() => {})
      } else if (scenario === 'error-route') {
        await page.route('**/assets/AssistantView-*.js', (route) => route.abort())
        await page.getByRole('link', { name: '操作助手', exact: true }).click()
      } else if (scenario.startsWith('link'))
        await page.getByRole('link', { name: '我的预约', exact: true }).click()
      else await page.goBack({ waitUntil: 'commit', timeout: 1500 }).catch(() => {})
      const dialog = page.getByRole('dialog', { name: '放弃预约草稿？' })
      if (scenario === 'pending-back') {
        // A popstate updates the address bar before Vue Router restores a rejected navigation.
        await page.waitForURL(/\/equipment/)
        assert.match(page.url(), /\/equipment/)
        assert.equal(await dialog.count(), 0)
        release()
        await form.getByRole('alert').filter({ hasText: '所选时段已被占用' }).waitFor()
        await page.goBack({ waitUntil: 'commit', timeout: 1500 }).catch(() => {})
        await dialog.waitFor()
        await dialog.getByRole('button', { name: '继续填写', exact: true }).click()
        assert.deepEqual(
          [await purpose.inputValue(), await start.inputValue(), await end.inputValue()],
          original,
        )
      } else if (scenario === 'error-route') {
        await dialog.waitFor()
        await dialog.getByRole('button', { name: '放弃草稿', exact: true }).click()
        await dialog.waitFor({ state: 'hidden' })
        await page
          .waitForResponse(
            (response) => response.url().includes('AssistantView-') && !response.ok(),
          )
          .catch(() => {})
        assert.match(page.url(), /\/equipment/)
        assert.deepEqual(
          [await purpose.inputValue(), await start.inputValue(), await end.inputValue()],
          original,
        )
      } else if (
        scenario === 'empty-back' ||
        scenario === 'expired-back' ||
        scenario === 'changed-back' ||
        scenario.startsWith('confirm-')
      ) {
        await page.waitForURL(
          scenario === 'expired-back' || scenario === 'confirm-expire' ? /\/login/ : /\/home/,
        )
        await dialog.waitFor({ state: 'hidden' })
        if (scenario === 'changed-back')
          assert.equal(await form.count(), 0, 'old account draft must be cleared')
      } else {
        await dialog.waitFor()
        await dialog
          .getByRole('button', {
            name: scenario.endsWith('continue') ? '继续填写' : '放弃草稿',
            exact: true,
          })
          .click()
        await dialog.waitFor({ state: 'hidden' })
        if (scenario.endsWith('continue')) {
          assert.match(page.url(), /\/equipment/)
          assert.equal(await form.isVisible(), true)
          assert.deepEqual(
            [await purpose.inputValue(), await start.inputValue(), await end.inputValue()],
            original,
          )
          await page.locator('.planning-content h2').filter({ hasText: '数字示波器' }).waitFor()
        } else {
          await page.waitForURL(scenario.startsWith('link') ? /\/reservations/ : /\/home/)
          await page.getByRole('link', { name: '找设备', exact: true }).click()
          await page.getByRole('button', { name: '查看数字示波器排期', exact: true }).click()
          await page.getByRole('button', { name: '申请这台设备', exact: true }).click()
          assert.deepEqual(
            [await purpose.inputValue(), await start.inputValue(), await end.inputValue()],
            ['', '', ''],
          )
        }
      }
      const journal = await (await page.request.get(`${base}/__fixture/journal`)).json()
      assert.equal(
        journal.data.filter((entry) => entry.method !== 'GET').length,
        scenario === 'pending-back' ? 1 : 0,
      )
      results.push({ name, passed: true })
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
