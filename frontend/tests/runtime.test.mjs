import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextTick } from 'vue'
import { mount, flatten, text, button, settle, deferred, waiting, json } from './ui-helpers.mjs'
const textarea = root => flatten(root).find(n => n.tag === 'textarea')
async function draft(ui, value) {
  const el = textarea(ui.root); el.value = value; el.listeners.input({ target: el }); await nextTick()
}
const completed = { ...waiting('20'), status: 'COMPLETED', currentPhase: null, currentQuestion: null }

test('Answer blocks blank/duplicate commands, sends current question and rereads authoritative next question', async t => {
  const post = deferred(); const read = deferred(); const calls = []; let reads = 0
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push({ path, ...init })
    if (init.method === 'POST') return post.promise
    return ++reads === 1 ? json(waiting('20')) : read.promise
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  assert.equal(button(ui.root, 'Submit').props.disabled, true)
  await draft(ui, '  '); await button(ui.root, 'Submit').props.onClick(); assert.equal(calls.length, 1)
  await draft(ui, '我的回答')
  const send = button(ui.root, 'Submit').props.onClick(); await nextTick()
  assert.match(text(ui.root), /正在评价回答并生成下一步问题，可能需要一些时间…/)
  for (const label of ['Submit', 'Skip', 'Pause', 'Finish', '刷新状态']) assert.equal(button(ui.root, label).props.disabled, true)
  await button(ui.root, 'Pause').props.onClick(); assert.equal(calls.length, 2)
  assert.match(calls[1].path, /questions\/31\/answer$/)
  assert.deepEqual(JSON.parse(calls[1].body), { content: '我的回答' })
  post.resolve(json({ answer: { id: '1' }, evaluation: {} })); await settle()
  assert.equal(button(ui.root, 'Submit').props.disabled, true)
  assert.match(text(ui.root), /正在评价回答并生成下一步问题，可能需要一些时间…/)
  read.resolve(json({ ...waiting('20'), currentQuestion: { ...waiting('20').currentQuestion, id: '32', content: '下一题' } }))
  await send; await settle()
  assert.match(text(ui.root), /下一题/); assert.equal(textarea(ui.root).value, '')
  assert.deepEqual(calls.map(c => c.method), ['GET', 'POST', 'GET'])
})

test('Pause keeps draft read-only; Resume restores editing; Finish confirms and remains on Session', async t => {
  let snapshot = waiting('20'); const calls = []; let confirm = false
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push(path)
    if (init.method === 'POST') {
      if (path.endsWith('/pause')) snapshot = { ...snapshot, status: 'PAUSED' }
      if (path.endsWith('/resume')) snapshot = { ...snapshot, status: 'IN_PROGRESS' }
      if (path.endsWith('/finish')) snapshot = completed
      return json(waiting('20')) // deliberately obsolete command response
    }
    return json(snapshot)
  })
  t.mock.property(globalThis, 'window', { confirm: () => confirm })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  await draft(ui, '保留草稿'); await button(ui.root, 'Pause').props.onClick(); await settle()
  assert.equal(textarea(ui.root).props.readonly, true); assert.equal(textarea(ui.root).value, '保留草稿')
  assert.equal(button(ui.root, 'Submit'), undefined)
  await button(ui.root, 'Resume').props.onClick(); await settle()
  assert.equal(textarea(ui.root).props.readonly, false); assert.equal(textarea(ui.root).value, '保留草稿')
  const before = calls.length
  await button(ui.root, 'Finish').props.onClick(); assert.equal(calls.length, before)
  confirm = true; await button(ui.root, 'Finish').props.onClick(); await settle()
  assert.match(text(ui.root), /COMPLETED/); assert.match(text(ui.root), /查看面试复盘/)
  assert.deepEqual(ui.paths, [])
  await button(ui.root, '查看面试复盘').props.onClick()
  assert.deepEqual(ui.paths, ['/interviews/20/review'])
})

test('Skip confirms, keeps WAITING_ANSWER while pending and clears draft only after GET changes question', async t => {
  const post = deferred(); let reads = 0; let confirmed = false; let writes = 0
  t.mock.property(globalThis, 'window', { confirm: () => confirmed })
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'POST') { writes++; return post.promise }
    return json(++reads === 1 ? waiting('20') : completed)
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle(); await draft(ui, '草稿')
  await button(ui.root, 'Skip').props.onClick(); assert.equal(writes, 0)
  confirmed = true; const skip = button(ui.root, 'Skip').props.onClick(); await nextTick()
  assert.match(text(ui.root), /正在跳过当前问题并准备下一步，可能需要一些时间…/)
  assert.match(text(ui.root), /WAITING_ANSWER/); assert.doesNotMatch(text(ui.root), /PROCESSING_TURN/)
  post.resolve(json(waiting('20'))); await skip; await settle(); assert.match(text(ui.root), /COMPLETED/)
})

test('failed command rereads and retains draft and error; successful submit with failed GET blocks commands', async t => {
  let writes = 0; let reads = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'POST') return ++writes === 1
      ? new Response(JSON.stringify({ code: 23002, message: '冲突', requestId: 'runtime-id', data: null }), { status: 409 })
      : json({ answer: {}, evaluation: {} })
    if (++reads === 3) throw new TypeError('GET failed')
    return json(reads >= 5
      ? { ...waiting('20'), currentQuestion: { ...waiting('20').currentQuestion, id: '32', content: '下一题' } }
      : waiting('20'))
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle(); await draft(ui, '保留')
  await button(ui.root, 'Submit').props.onClick(); await settle()
  assert.equal(reads, 2); assert.equal(textarea(ui.root).value, '保留')
  assert.match(text(ui.root), /冲突/); assert.match(text(ui.root), /runtime-id/)
  await button(ui.root, 'Submit').props.onClick(); await settle()
  assert.equal(textarea(ui.root).value, '保留'); assert.match(text(ui.root), /读取失败/)
  assert.match(text(ui.root), /快照可能过期/)
  for (const label of ['Submit', 'Skip', 'Pause', 'Finish']) assert.equal(button(ui.root, label).props.disabled, true)
  await button(ui.root, 'Submit').props.onClick(); assert.equal(writes, 2)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.equal(textarea(ui.root).value, '保留') // Same authoritative question retains its draft.
  assert.equal(button(ui.root, 'Submit').props.disabled, false)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.match(text(ui.root), /下一题/); assert.equal(textarea(ui.root).value, '')
})


test('lost POST response still clears old draft when authoritative GET changes question', async t => {
  let reads = 0; let writes = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'POST') { writes++; throw new TypeError('POST response lost') }
    return json(++reads === 1 ? waiting('20')
      : { ...waiting('20'), currentQuestion: { ...waiting('20').currentQuestion, id: '32', content: '下一题' } })
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  await draft(ui, '旧题回答'); await button(ui.root, 'Submit').props.onClick(); await settle()
  assert.equal(writes, 1); assert.equal(reads, 2)
  assert.match(text(ui.root), /操作失败：网络请求失败/); assert.match(text(ui.root), /下一题/)
  assert.equal(textarea(ui.root).value, '')
})
