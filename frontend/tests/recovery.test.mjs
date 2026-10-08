import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextTick } from 'vue'
import { mount, flatten, text, button, settle, deferred, waiting, json } from './ui-helpers.mjs'

const processing = id => ({ ...waiting(id), currentPhase: 'PROCESSING_TURN' })
const completed = id => ({ ...waiting(id), status: 'COMPLETED', currentPhase: null, currentQuestion: null })
const textarea = root => flatten(root).find(n => n.tag === 'textarea')
const error = message => new Response(JSON.stringify({ code: 23002, message, requestId: 'recovery-id', data: null }), { status: 409 })

test('Submit error is visible until authoritative GET succeeds; failed manual refresh preserves errors', async t => {
  const reconciliation = deferred(); let reads = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'POST') return error('本轮处理异常')
    reads++
    if (reads === 1) return json(waiting('20'))
    if (reads === 2) return reconciliation.promise
    if (reads === 3) return error('状态读取异常')
    return json(processing('20'))
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  const el = textarea(ui.root); el.value = '草稿'; el.listeners.input({ target: el }); await nextTick()
  const send = button(ui.root, 'Submit').props.onClick(); await settle()
  assert.match(text(ui.root), /本轮处理异常/); assert.match(text(ui.root), /recovery-id/)
  reconciliation.resolve(error('状态读取异常')); await send; await settle()
  assert.match(text(ui.root), /本轮处理异常/); assert.match(text(ui.root), /读取失败/)
  assert.match(text(ui.root), /快照可能过期/)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.match(text(ui.root), /本轮处理异常/); assert.match(text(ui.root), /recovery-id/)
  assert.match(text(ui.root), /读取失败/); assert.match(text(ui.root), /快照可能过期/)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.match(text(ui.root), /本轮处理尚未完成/)
  assert.doesNotMatch(text(ui.root), /本轮处理异常|recovery-id|读取失败|快照可能过期/)
  assert.equal(button(ui.root, '重试本轮处理').props.disabled, false)
})

test('direct PROCESSING_TURN offers manual recovery without answer commands or polling', async t => {
  const calls = []; let snapshot = processing('20')
  t.mock.method(globalThis, 'fetch', async (path, init) => { calls.push({ path, ...init }); return json(snapshot) })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  assert.match(text(ui.root), /本轮处理尚未完成/)
  assert.equal(textarea(ui.root), undefined)
  for (const label of ['Submit', 'Skip', 'Pause', 'Finish']) assert.equal(button(ui.root, label), undefined)
  assert.equal(button(ui.root, '重试本轮处理').props.disabled, false)
  await settle(); assert.equal(calls.length, 1)
  snapshot = waiting('20')
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.ok(textarea(ui.root)); assert.equal(button(ui.root, '重试本轮处理'), undefined)
  assert.deepEqual(calls.map(c => c.method), ['GET', 'GET'])
})

test('Retry sends no draft, blocks duplicate requests and waits for authoritative GET', async t => {
  const post = deferred(); const read = deferred(); const calls = []; let reads = 0
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push({ path, ...init })
    return init.method === 'POST' ? post.promise : ++reads === 1 ? json(processing('20')) : read.promise
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  const retry = button(ui.root, '重试本轮处理'); const send = retry.props.onClick(); await nextTick()
  assert.match(text(ui.root), /正在重新处理本轮回答，可能需要一些时间…/)
  assert.equal(retry.props.disabled, true); assert.equal(button(ui.root, '刷新状态').props.disabled, true)
  await retry.props.onClick(); assert.equal(calls.length, 2)
  assert.equal(calls[1].path, '/api/interviews/20/retry'); assert.equal(calls[1].body, undefined)
  post.resolve(json({ answer: {}, evaluation: {} })); await settle()
  assert.equal(retry.props.disabled, true); assert.equal(textarea(ui.root), undefined)
  assert.match(text(ui.root), /正在重新处理本轮回答，可能需要一些时间…/)
  read.resolve(json(completed('20'))); await send; await settle()
  assert.match(text(ui.root), /面试已完成/)
  assert.deepEqual(calls.map(c => c.method), ['GET', 'POST', 'GET'])
})

test('command conflicts, expiry and uncertain network outcomes preserve errors after reconciliation; next successful command clears them', async t => {
  for (const failure of ['状态冲突', '面试已过期', 'network']) {
    const calls = []; let reads = 0; let writes = 0
    t.mock.method(globalThis, 'fetch', async (path, init) => {
      calls.push({ path, ...init })
      if (init.method === 'POST') {
        if (++writes > 1) return json({ answer: {}, evaluation: {} })
        if (failure === 'network') throw new TypeError('lost response')
        return error(failure)
      }
      return json(++reads === 1 ? waiting('20') : processing('20'))
    })
    const ui = await mount('InterviewPage'); await settle()
    const el = textarea(ui.root); el.value = '保留草稿'; el.listeners.input({ target: el }); await nextTick()
    await button(ui.root, 'Submit').props.onClick(); await settle()
    assert.deepEqual(calls.map(c => c.method), ['GET', 'POST', 'GET'])
    assert.match(calls[1].path, /\/answer$/); assert.equal(textarea(ui.root), undefined)
    assert.match(text(ui.root), /操作失败/)
    assert.match(text(ui.root), failure === 'network' ? /网络请求失败/ : new RegExp(failure))
    if (failure !== 'network') assert.match(text(ui.root), /recovery-id/)
    await button(ui.root, '重试本轮处理').props.onClick(); await settle()
    assert.equal(calls[3].path, '/api/interviews/20/retry')
    assert.deepEqual(calls.map(c => c.method), ['GET', 'POST', 'GET', 'POST', 'GET'])
    assert.doesNotMatch(text(ui.root), /操作失败|recovery-id/)
    ui.app.unmount(); t.mock.restoreAll()
  }
})

test('Retry failed reread blocks recovery until refresh; same question preserves draft', async t => {
  let reads = 0; let writes = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'POST') { writes++; return error('处理未完成') }
    reads++
    if (reads === 3) throw new TypeError('GET failed')
    return json(reads === 2 || reads === 4 ? processing('20') : waiting('20'))
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  const el = textarea(ui.root); el.value = '原题草稿'; el.listeners.input({ target: el }); await nextTick()
  await button(ui.root, 'Submit').props.onClick(); await settle()
  await button(ui.root, '重试本轮处理').props.onClick(); await settle()
  assert.match(text(ui.root), /快照可能过期/); assert.match(text(ui.root), /读取失败/)
  assert.match(text(ui.root), /处理未完成|recovery-id/)
  assert.equal(button(ui.root, '重试本轮处理').props.disabled, true)
  await button(ui.root, '重试本轮处理').props.onClick(); assert.equal(writes, 2)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.equal(button(ui.root, '重试本轮处理').props.disabled, false)
  assert.doesNotMatch(text(ui.root), /操作失败|recovery-id|读取失败|快照可能过期/)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.equal(textarea(ui.root).value, '原题草稿')
})

test('late Retry response cannot update a different Interview or trigger its reconciliation; unmount aborts', async t => {
  const post = deferred(); const calls = []
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push({ path, ...init })
    return init.method === 'POST' ? post.promise : json(path.endsWith('/21') ? waiting('21') : processing('20'))
  })
  const ui = await mount('InterviewPage'); await settle()
  const send = button(ui.root, '重试本轮处理').props.onClick(); await nextTick()
  ui.route.params.id = '21'; await settle()
  assert.equal(calls[1].signal.aborted, true)
  post.resolve(json({ answer: {}, evaluation: {} })); await send; await settle()
  assert.ok(textarea(ui.root)); assert.equal(button(ui.root, '重试本轮处理'), undefined)
  assert.deepEqual(calls.map(c => c.path), ['/api/interviews/20', '/api/interviews/20/retry', '/api/interviews/21'])
  ui.app.unmount(); assert.equal(calls[2].signal.aborted, true)
})
