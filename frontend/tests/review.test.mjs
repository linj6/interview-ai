import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextTick } from 'vue'
import { mount, flatten, text, button, settle, deferred, json, created, waiting, profile } from './ui-helpers.mjs'

const review = id => ({
  interviewId: id,
  overview: { startedAt: '2026-10-03T10:00:00', completedAt: '2026-10-03T10:03:00',
    durationSeconds: 180, mainQuestionCount: 2, followUpQuestionCount: 1,
    answeredCount: 1, skippedCount: 1, unansweredCount: 1 },
  questions: [
    { questionId: '31', content: '主问题', result: 'ANSWERED', answer: '我的回答',
      evaluation: { overallScore: 7.5, strengths: ['清晰'], weaknesses: ['不够深入'],
        missingPoints: [{ description: '遗漏细节', importance: 'HIGH', explanation: '补充边界' }],
        referenceAnswer: { answerForm: 'ANSWER_FRAMEWORK', keyPoints: ['关键要点'], content: '参考内容' },
        dimensionScores: [{ name: '准确性', score: 8, explanation: '基本准确' }] },
      followUps: [{ questionId: '32', content: '追问内容', result: 'UNANSWERED', answer: null, evaluation: null }] },
    { questionId: '33', content: '跳过问题', result: 'SKIPPED', answer: null, evaluation: null, followUps: [] },
  ],
})

test('direct Review loads GET, renders backend overview, question chain and Evaluation, navigates back', async t => {
  const response = deferred(); const calls = []
  t.mock.method(globalThis, 'fetch', (path, init) => { calls.push([path, init.method]); return response.promise })
  const ui = await mount('ReviewPage'); t.after(() => ui.app.unmount())
  assert.match(text(ui.root), /正在读取面试复盘/)
  assert.equal(button(ui.root, '重新加载').props.disabled, true)
  response.resolve(json(review('20'))); await settle()
  assert.deepEqual(calls, [['/api/interviews/20/review', 'GET']])
  for (const value of ['180 秒', 'MAIN：2', 'FOLLOW_UP：1', 'ANSWERED', 'SKIPPED', 'UNANSWERED',
    '我的回答', '无回答记录', '无评价记录', '7.5', '清晰', '不够深入', '遗漏细节', '补充边界',
    '准确性', '基本准确', '关键要点', '参考内容', '2026-10-03T10:00:00']) {
    assert.ok(text(ui.root).includes(value), value)
  }
  assert.deepEqual(flatten(ui.root).filter(n => n.tag === 'article').map(n => n.props['aria-label']),
    ['MAIN', 'FOLLOW_UP', 'MAIN'])
  await button(ui.root, '返回 Session').props.onClick()
  assert.deepEqual(ui.paths, ['/interviews/20'])
})

test('not found, incomplete, network and null Review give retryable feedback', async t => {
  for (const response of [
    () => new Response(JSON.stringify({ code: 23001, message: '面试不存在', data: null, requestId: 'review-error' }), { status: 404 }),
    () => new Response(JSON.stringify({ code: 23002, message: '面试尚未完成', data: null, requestId: 'review-error' }), { status: 409 }),
    () => { throw new TypeError('network') },
    () => json(null),
    () => json(review('21')),
  ]) {
    t.mock.method(globalThis, 'fetch', response)
    const ui = await mount('ReviewPage'); await settle()
    assert.match(text(ui.root), /读取失败/)
    assert.equal(button(ui.root, '重新加载').props.disabled, false)
    t.mock.restoreAll()
    t.mock.method(globalThis, 'fetch', async () => json({ ...review('20'), questions: [] }))
    await button(ui.root, '重新加载').props.onClick(); await settle()
    assert.match(text(ui.root), /暂无问题记录/)
    assert.doesNotMatch(text(ui.root), /读取失败/)
    ui.app.unmount(); t.mock.restoreAll()
  }
})

test('error exposes requestId and invalid route never fetches', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    code: 23002, message: '未完成', data: null, requestId: 'review-error',
  }), { status: 409 }))
  const ui = await mount('ReviewPage'); await settle()
  assert.match(text(ui.root), /requestId：review-error/); ui.app.unmount()
  let calls = 0
  t.mock.method(globalThis, 'fetch', async () => { calls++; return json(review('20')) })
  for (const id of ['0', '-1', 'abc', '9223372036854775808']) {
    const invalid = await mount('ReviewPage', id); await settle()
    assert.match(text(invalid.root), /Long 范围内的正整数十进制字符串/)
    await button(invalid.root, '返回首页').props.onClick()
    assert.deepEqual(invalid.paths, ['/']); invalid.app.unmount()
  }
  assert.equal(calls, 0)
})

test('route change and unmount abort reads and ignore late responses', async t => {
  const old = deferred(); const signals = []
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    signals.push(init.signal)
    return path.includes('/20/') ? old.promise : json(review('21'))
  })
  const ui = await mount('ReviewPage')
  ui.route.params.id = '21'; await nextTick(); await settle()
  assert.equal(signals[0].aborted, true)
  old.resolve(json(review('20'))); await settle()
  await button(ui.root, '返回 Session').props.onClick()
  assert.deepEqual(ui.paths, ['/interviews/21'])
  ui.app.unmount()
  assert.equal(signals[1].aborted, true)
})

test('mock closure connects create, Start, Pause/Resume, Answer, Skip, Finish and Review; Answer can auto-complete', async t => {
  for (const automatic of [false, true]) {
    let snapshot = created('20')
    const calls = []
    t.mock.property(globalThis, 'window', { confirm: () => true })
    t.mock.method(globalThis, 'fetch', async (path, init) => {
      calls.push(path)
      if (path === '/api/interview-profile') return json(profile())
      if (path.endsWith('/review')) return json(review('20'))
      if (init.method === 'POST') {
        if (path.endsWith('/start')) snapshot = waiting('20')
        if (path.endsWith('/pause')) snapshot = { ...snapshot, status: 'PAUSED' }
        if (path.endsWith('/resume')) snapshot = { ...snapshot, status: 'IN_PROGRESS' }
        if (path.endsWith('/answer') || path.endsWith('/skip')) {
          snapshot = { ...waiting('20'), currentQuestion: { ...waiting('20').currentQuestion, id: '32' } }
        }
        if (path.endsWith('/finish') || (automatic && path.endsWith('/answer'))) {
          snapshot = { ...snapshot, status: 'COMPLETED', currentPhase: null, currentQuestion: null }
        }
      }
      return json(snapshot)
    })
    const home = await mount('HomePage'); await settle()
    flatten(home.root).find(n => n.props.id === 'interview-mode').props.onChange({ target: { value: 'COMPREHENSIVE' } }); await settle()
    await button(home.root, '创建').props.onClick(); await settle()
    assert.deepEqual(home.paths, ['/interviews/20']); home.app.unmount()
    const session = await mount('InterviewPage'); await settle()
    await button(session.root, 'Start').props.onClick(); await settle()
    await button(session.root, 'Pause').props.onClick(); await settle()
    await button(session.root, 'Resume').props.onClick(); await settle()
    const input = flatten(session.root).find(n => n.tag === 'textarea')
    input.value = '完整回答'; input.listeners.input({ target: input }); await nextTick()
    await button(session.root, 'Submit').props.onClick(); await settle()
    if (!automatic) {
      await button(session.root, 'Skip').props.onClick(); await settle()
      await button(session.root, 'Finish').props.onClick(); await settle()
    }
    await button(session.root, '查看面试复盘').props.onClick()
    assert.deepEqual(session.paths, ['/interviews/20/review']); session.app.unmount()
    const result = await mount('ReviewPage'); await settle()
    assert.match(text(result.root), /Overview/)
    assert.equal(calls.at(-1), '/api/interviews/20/review')
    result.app.unmount(); t.mock.restoreAll()
  }
})
