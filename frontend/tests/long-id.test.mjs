import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextTick } from 'vue'
import { mount, flatten, text, button, settle, json, created, waiting, profile } from './ui-helpers.mjs'

const id = '1975511234567890123'
const questionId = '1975511234567890124'
const nextQuestionId = '1975511234567890125'

test('large ID Create → route → Prepare → Session → Answer/Retry/Skip → Review stays exact', async t => {
  let snapshot = { ...created(id), planPrepared: false }
  const calls = []
  t.mock.property(globalThis, 'window', { confirm: () => true })
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push([path, init.method, init.body])
    if (path === '/api/interview-profile') return json(profile([]))
    if (path.endsWith('/review')) return json({
      interviewId: id, overview: { durationSeconds: 180, mainQuestionCount: 1, followUpQuestionCount: 1,
        answeredCount: 1, skippedCount: 1, unansweredCount: 0 },
      questions: [{ questionId, content: '主问题', result: 'ANSWERED', answer: '回答', evaluation: null,
        followUps: [{ questionId: nextQuestionId, content: '追问', result: 'SKIPPED', answer: null, evaluation: null }] }],
    })
    if (path.endsWith('/prepare')) {
      snapshot = { ...snapshot, planPrepared: true }
      return json({ interviewId: id, planId: '1975511234567890130', initialPlanItemId: '1975511234567890131' })
    }
    if (path.endsWith('/start')) snapshot = { ...waiting(id), currentQuestion: { ...waiting(id).currentQuestion, id: questionId } }
    if (path.endsWith('/answer')) snapshot = { ...snapshot, currentPhase: 'PROCESSING_TURN' }
    if (path.endsWith('/retry')) snapshot = { ...snapshot, currentPhase: 'WAITING_ANSWER', currentQuestion: { ...snapshot.currentQuestion, id: nextQuestionId } }
    if (path.endsWith('/answer') || path.endsWith('/retry')) return json({
      answer: { id: '1975511234567890140', content: '回答' },
      evaluation: { id: '1975511234567890150', profileId: '1', overallScore10: 8, overallScore100: 80 },
    })
    if (path.endsWith('/skip')) snapshot = { ...snapshot, status: 'COMPLETED', currentPhase: null, currentQuestion: null }
    return json(snapshot)
  })
  const home = await mount('HomePage'); t.after(() => home.app.unmount()); await settle()
  flatten(home.root).find(n => n.props.id === 'interview-mode').props.onChange({ target: { value: 'COMPREHENSIVE' } })
  await nextTick(); await button(home.root, '创建面试').props.onClick(); await settle()
  assert.deepEqual(home.paths, [`/interviews/${id}`])

  const session = await mount('InterviewPage', id); t.after(() => session.app.unmount()); await settle()
  await button(session.root, 'Prepare').props.onClick(); await settle()
  await button(session.root, 'Start').props.onClick(); await settle()
  assert.match(text(session.root), new RegExp(`Question ID：${questionId}`))
  const draft = flatten(session.root).find(n => n.tag === 'textarea')
  draft.value = '回答'; draft.listeners.input({ target: draft }); await nextTick()
  await button(session.root, 'Submit').props.onClick(); await settle()
  await button(session.root, '重试本轮处理').props.onClick(); await settle()
  await button(session.root, 'Skip').props.onClick(); await settle()
  await button(session.root, '查看面试复盘').props.onClick()
  assert.deepEqual(session.paths, [`/interviews/${id}/review`])
  assert.ok(calls.some(([path, method, body]) => path === `/api/interviews/${id}/questions/${questionId}/answer`
    && method === 'POST' && JSON.parse(body).content === '回答'))
  assert.ok(calls.some(([path, method]) => path === `/api/interviews/${id}/questions/${nextQuestionId}/skip` && method === 'POST'))

  const review = await mount('ReviewPage', id); t.after(() => review.app.unmount()); await settle()
  assert.match(text(review.root), new RegExp(`Question ${questionId}`))
  assert.match(text(review.root), new RegExp(`Question ${nextQuestionId}`))
  assert.match(text(review.root), /时长：180 秒/)
})

test('routes accept Long.MAX_VALUE and reject noncanonical or out-of-range decimal IDs', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async path => { calls.push(path); return json(created('9223372036854775807')) })
  const valid = await mount('InterviewPage', '9223372036854775807'); await settle()
  assert.deepEqual(calls, ['/api/interviews/9223372036854775807']); valid.app.unmount()
  for (const invalid of ['0', '01', '+1', '-1', ' 1', '1e3', '9223372036854775808']) {
    for (const page of ['InterviewPage', 'ReviewPage']) {
      const ui = await mount(page, invalid); await settle()
      assert.match(text(ui.root), /Long 范围内的正整数十进制字符串/)
      ui.app.unmount()
    }
  }
  assert.equal(calls.length, 1)
})
