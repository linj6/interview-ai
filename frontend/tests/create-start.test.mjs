import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextTick } from 'vue'
import { mount, flatten, text, button, settle, deferred, created, opening, waiting, json, profile } from './ui-helpers.mjs'

test('Home create uses returned ID and blocks duplicate clicks', async t => {
  const response = deferred(); let calls = 0
  t.mock.method(globalThis, 'fetch', async path => {
    if (path === '/api/interview-profile') return json(profile())
    calls++; return response.promise
  })
  const ui = await mount('HomePage'); t.after(() => ui.app.unmount())
  await settle()
  flatten(ui.root).find(n => n.props.id === 'interview-mode').props.onChange({ target: { value: 'COMPREHENSIVE' } }); await settle()
  const create = button(ui.root, '创建面试')
  const first = create.props.onClick(); await nextTick()
  assert.equal(create.props.disabled, true)
  await create.props.onClick(); assert.equal(calls, 1)
  response.resolve(json(created('42'))); await first; await settle()
  assert.deepEqual(ui.paths, ['/interviews/42'])
})

test('Start pending blocks commands; fresh GET wins over POST response', async t => {
  const response = deferred(); const calls = []; let reads = 0
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push(init.method)
    if (init.method === 'POST') return response.promise
    return json(++reads === 1 ? created('20') : waiting('20'))
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  const start = button(ui.root, 'Start'); const first = start.props.onClick(); await nextTick()
  assert.match(text(ui.root), /正在生成第一道面试问题，可能需要一些时间…/)
  assert.doesNotMatch(text(ui.root), /请等待并刷新状态/)
  assert.equal(start.props.disabled, true)
  assert.equal(button(ui.root, '刷新状态').props.disabled, true)
  await start.props.onClick(); assert.deepEqual(calls, ['GET', 'POST'])
  response.resolve(json(opening('20'))); await first; await settle()
  assert.deepEqual(calls, ['GET', 'POST', 'GET'])
  assert.match(text(ui.root), /当前问题内容/)
  assert.equal(button(ui.root, 'Start'), undefined)
})

test('Start error survives OPENING reconciliation; explicit refresh recovers question and clears error', async t => {
  let reads = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => init.method === 'POST'
    ? new Response(JSON.stringify({ code: 23002, message: '状态冲突', data: null, requestId: 'conflict-id' }), { status: 409 })
    : json([created('20'), opening('20'), waiting('20')][reads++]))
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  await button(ui.root, 'Start').props.onClick(); await settle()
  assert.match(text(ui.root), /状态冲突/); assert.match(text(ui.root), /conflict-id/)
  assert.match(text(ui.root), /正在生成第一道面试问题，可能需要一些时间…/)
  assert.equal(button(ui.root, 'Start'), undefined)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.match(text(ui.root), /当前问题内容/)
})

test('failed reread preserves snapshot but blocks Start until successful refresh', async t => {
  let reads = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'POST') throw new TypeError('lost POST response')
    if (++reads === 2) throw new TypeError('lost GET response')
    return json(created('20'))
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  await button(ui.root, 'Start').props.onClick(); await settle()
  assert.match(text(ui.root), /快照可能过期/)
  assert.equal(button(ui.root, 'Start').props.disabled, true)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.equal(button(ui.root, 'Start').props.disabled, false)
})

test('direct OPENING load refreshes to currentQuestion, never substitutes firstQuestion', async t => {
  let reads = 0
  t.mock.method(globalThis, 'fetch', async () => json(++reads === 1 ? opening('20')
    : { ...waiting('20'), firstQuestion: { ...waiting('20').currentQuestion, content: '旧首题' } }))
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  assert.match(text(ui.root), /正在生成第一道面试问题，可能需要一些时间…/)
  assert.equal(button(ui.root, 'Start'), undefined)
  await button(ui.root, '刷新状态').props.onClick(); await settle()
  assert.match(text(ui.root), /当前问题内容/); assert.doesNotMatch(text(ui.root), /旧首题/)
})

test('missing current question and invalid state combinations expose no Start', async t => {
  for (const snapshot of [{ ...waiting('20'), currentQuestion: null }, { ...created('20'), currentPhase: 'OPENING' }]) {
    t.mock.method(globalThis, 'fetch', async () => json(snapshot))
    const ui = await mount('InterviewPage'); await settle()
    assert.equal(button(ui.root, 'Start'), undefined)
    assert.match(text(ui.root), /此状态暂不支持操作/)
    ui.app.unmount(); t.mock.restoreAll()
  }
})

test('route validation, late response protection and unmount cancellation', async t => {
  const response = deferred(); const signals = []
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    signals.push(init.signal)
    return path.endsWith('/20') ? response.promise : json(waiting('21'))
  })
  const invalid = await mount('InterviewPage', '9223372036854775808'); await settle()
  assert.match(text(invalid.root), /Long 范围内/); assert.equal(signals.length, 0); invalid.app.unmount()
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount())
  ui.route.params.id = '21'; await settle()
  assert.equal(signals[0].aborted, true)
  response.resolve(json(created('20'))); await settle()
  assert.match(text(ui.root), /当前问题内容/)
  assert.equal(button(ui.root, 'Start'), undefined)
  ui.app.unmount(); assert.equal(signals[1].aborted, true)
})

const choose = async (ui, id, value) => {
  flatten(ui.root).find(n => n.props.id === id).props.onChange({ target: { value } })
  await settle()
}

test('mode is required; technical area is required and cleared on mode changes', async t => {
  const bodies = []
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    if (path === '/api/interview-profile') return json(profile())
    bodies.push(JSON.parse(init.body)); return json(created('42'))
  })
  const ui = await mount('HomePage'); t.after(() => ui.app.unmount())
  await settle()
  await button(ui.root, '创建面试').props.onClick(); assert.equal(bodies.length, 0)
  await choose(ui, 'interview-mode', 'TECHNICAL_FOCUS')
  assert.equal(flatten(ui.root).filter(n => n.tag === 'option').length, 14)
  await button(ui.root, '创建面试').props.onClick(); assert.equal(bodies.length, 0)
  await choose(ui, 'focus-area', 'MYSQL')
  await button(ui.root, '创建面试').props.onClick()
  assert.deepEqual(bodies[0], { mode: 'TECHNICAL_FOCUS', focusArea: 'MYSQL' })
  await choose(ui, 'interview-mode', 'PROJECT_DEEP_DIVE')
  assert.equal(flatten(ui.root).find(n => n.props.id === 'focus-area'), undefined)
  await button(ui.root, '创建面试').props.onClick()
  assert.deepEqual(bodies[1], { mode: 'PROJECT_DEEP_DIVE' })
  await choose(ui, 'interview-mode', 'TECHNICAL_FOCUS')
  assert.equal(button(ui.root, '创建面试').props.disabled, true)
})

test('Prepare waits for GET confirmation, blocks duplicates and does not auto Start', async t => {
  const post = deferred(); const read = deferred(); const calls = []; let reads = 0
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push(path)
    if (init.method === 'POST') return post.promise
    return ++reads === 1 ? json({ ...created('20'), planPrepared: false }) : read.promise
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  assert.equal(button(ui.root, 'Start'), undefined)
  const prepare = button(ui.root, 'Prepare'); const operation = prepare.props.onClick(); await settle()
  assert.match(text(ui.root), /正在生成面试计划，可能需要一些时间…/)
  assert.equal(prepare.props.disabled, true)
  await prepare.props.onClick(); assert.equal(calls.length, 2)
  post.resolve(json({ interviewId: '20', planId: '1', initialPlanItemId: '2' })); await settle()
  assert.equal(button(ui.root, 'Start'), undefined)
  assert.equal(button(ui.root, 'Prepare').props.disabled, true)
  assert.match(text(ui.root), /正在生成面试计划，可能需要一些时间…/)
  read.resolve(json(created('20'))); await operation; await settle()
  assert.equal(button(ui.root, 'Prepare'), undefined)
  assert.equal(button(ui.root, 'Start').props.disabled, false)
  assert.deepEqual(calls, ['/api/interviews/20', '/api/interviews/20/prepare', '/api/interviews/20'])
})

test('uncertain Prepare reconciles prepared, unprepared or unreadable states without replay', async t => {
  for (const outcome of ['prepared', 'unprepared', 'unreadable']) {
    let reads = 0; let posts = 0
    t.mock.method(globalThis, 'fetch', async (_path, init) => {
      if (init.method === 'POST') { posts++; throw new TypeError('lost response') }
      reads++
      if (reads === 2 && outcome === 'unreadable') throw new TypeError('lost read')
      return json({ ...created('20'), planPrepared: reads > 1 && outcome !== 'unprepared' })
    })
    const ui = await mount('InterviewPage'); await settle()
    await button(ui.root, 'Prepare').props.onClick(); await settle()
    assert.equal(posts, 1)
    assert.match(text(ui.root), /操作失败：网络请求失败/)
    if (outcome === 'prepared') {
      assert.equal(button(ui.root, 'Prepare'), undefined)
      assert.equal(button(ui.root, 'Start').props.disabled, false)
    } else {
      assert.equal(button(ui.root, 'Start'), undefined)
      assert.equal(button(ui.root, 'Prepare').props.disabled, outcome === 'unreadable')
      assert.match(text(ui.root), /失败/)
      if (outcome === 'unreadable') {
        await button(ui.root, '刷新状态').props.onClick(); await settle()
        assert.equal(button(ui.root, 'Start').props.disabled, false)
        assert.equal(posts, 1)
      } else {
        await button(ui.root, 'Prepare').props.onClick(); await settle()
        assert.equal(posts, 2)
      }
    }
    ui.app.unmount(); t.mock.restoreAll()
  }
})

test('Prepare business failure remains visible when GET confirms no plan, then explicit retry succeeds', async t => {
  let prepared = false; let posts = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'POST') {
      if (++posts === 1) return new Response(JSON.stringify({ code: 23002, message: '准备失败', data: null, requestId: 'prepare-error' }), { status: 409 })
      prepared = true
      return json({ interviewId: '20', planId: '1', initialPlanItemId: '2' })
    }
    return json({ ...created('20'), planPrepared: prepared })
  })
  const ui = await mount('InterviewPage'); t.after(() => ui.app.unmount()); await settle()
  await button(ui.root, 'Prepare').props.onClick(); await settle()
  assert.match(text(ui.root), /准备失败/); assert.match(text(ui.root), /prepare-error/)
  assert.equal(button(ui.root, 'Prepare').props.disabled, false)
  assert.equal(button(ui.root, 'Start'), undefined)
  await button(ui.root, 'Prepare').props.onClick(); await settle()
  assert.equal(posts, 2); assert.equal(button(ui.root, 'Start').props.disabled, false)
  assert.doesNotMatch(text(ui.root), /prepare-error/)
})

test('missing or invalid planPrepared never permits Prepare or Start', async t => {
  for (const planPrepared of [undefined, null, 'true']) {
    t.mock.method(globalThis, 'fetch', async () => json({ ...created('20'), planPrepared }))
    const ui = await mount('InterviewPage'); await settle()
    assert.match(text(ui.root), /未返回有效的计划准备状态/)
    assert.equal(button(ui.root, 'Start'), undefined)
    assert.equal(button(ui.root, 'Prepare'), undefined)
    ui.app.unmount(); t.mock.restoreAll()
  }
})
