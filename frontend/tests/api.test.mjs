import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import ts from 'typescript'

// Use the existing TypeScript compiler and Node runner; no test framework or
// generated files. Type-only imports disappear during transpilation.
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const compile = source => ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const httpUrl = moduleUrl(compile(await readFile(new URL('../src/api/http.ts', import.meta.url), 'utf8')))
const { ApiError, http } = await import(httpUrl)
const api = await import(moduleUrl(compile(
  await readFile(new URL('../src/api/interview.ts', import.meta.url), 'utf8'),
).replaceAll("'./http'", JSON.stringify(httpUrl))))

// Minimal payloads: these tests exercise transport, not runtime DTO schemas.
const created = { id: '20', mode: 'COMPREHENSIVE', focusArea: null, planPrepared: false, status: 'CREATED', currentPhase: null, firstQuestion: null, currentQuestion: null, expiresAt: null }
const started = { ...created, status: 'IN_PROGRESS', currentPhase: 'WAITING_ANSWER',
  currentQuestion: { id: '31', type: 'MAIN', status: 'WAITING_ANSWER', content: '介绍项目' } }
const submission = { answer: { id: '40', content: '我的项目' }, evaluation: { id: '50' } }
const review = { interviewId: '20', overview: {}, questions: [] }
const envelope = data => ({ code: 0, message: '操作成功', data, requestId: 'body-id' })
const json = (payload, status = 200) => new Response(JSON.stringify(payload), {
  status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'header-id' },
})

test('Prepare and Answer/Retry decode exact large string IDs and preserve numeric fields', async t => {
  const id = '1975511234567890123'
  const plan = { interviewId: id, planId: '1975511234567890124', initialPlanItemId: '1975511234567890125' }
  const submission = { answer: { id: '1975511234567890126', content: '回答' },
    evaluation: { id: '1975511234567890127', profileId: '1975511234567890128', overallScore10: 8, overallScore100: 80 } }
  t.mock.method(globalThis, 'fetch', async path => json(envelope(path.endsWith('/prepare') ? plan : submission)))
  assert.deepEqual(await api.prepareInterview(id), plan)
  assert.deepEqual(await api.submitAnswer(id, '1975511234567890129', { content: '回答' }), submission)
  assert.deepEqual(await api.retryTurn(id), submission)
  assert.equal(typeof (await api.retryTurn(id)).evaluation.overallScore10, 'number')
})

test('all MVP endpoints use Controller paths, methods, bodies and return data', async t => {
  const cases = [
    ['createInterview', [{ mode: 'COMPREHENSIVE' }], '/api/interviews', 'POST', created],
    ['getInterview', ['20'], '/api/interviews/20', 'GET', created],
    ['prepareInterview', ['20'], '/api/interviews/20/prepare', 'POST', { interviewId: '20', planId: '1', initialPlanItemId: '2' }],
    ...['start', 'pause', 'resume', 'finish'].map(command =>
      [`${command}Interview`, ['20'], `/api/interviews/20/${command}`, 'POST', started]),
    ['submitAnswer', ['20', '31', { content: '我的项目' }], '/api/interviews/20/questions/31/answer', 'POST', submission],
    ['skipQuestion', ['20', '31'], '/api/interviews/20/questions/31/skip', 'POST', started],
    ['retryTurn', ['20'], '/api/interviews/20/retry', 'POST', submission],
    ['getReview', ['20'], '/api/interviews/20/review', 'GET', review],
  ]
  for (const [name, args, path, method, data] of cases) {
    let calls = 0
    const controller = new AbortController()
    t.mock.method(globalThis, 'fetch', async (url, init) => {
      calls++
      assert.equal(url, path)
      assert.equal(init.method, method)
      assert.equal(init.signal, controller.signal)
      assert.equal(init.headers.Accept, 'application/json')
      assert.equal(init.headers['X-Request-Id'], undefined)
      assert.equal(init.body, name === 'submitAnswer' ? JSON.stringify(args[2]) : name === 'createInterview' ? JSON.stringify(args[0]) : undefined)
      assert.equal(init.headers['Content-Type'], ['submitAnswer', 'createInterview'].includes(name) ? 'application/json' : undefined)
      return json(envelope(data))
    })
    assert.deepEqual(await api[name](...args, { signal: controller.signal }), data)
    assert.equal(calls, 1)
    t.mock.restoreAll()
  }
})

test('HTTP, business, network, non-JSON and malformed envelope failures are ApiError', async t => {
  const cases = [
    [() => json({ code: 23001, message: '面试不存在', data: null, requestId: 'body-id' }, 404), 'http', 404, 23001, 'body-id'],
    [() => json({ code: 23002, message: '状态冲突', data: null, requestId: null }), 'business', 200, 23002, 'header-id'],
    [() => json({ code: 23002, message: '状态冲突', data: null, requestId: ' ' }), 'business', 200, 23002, 'header-id'],
    [() => { throw new TypeError('private network details') }, 'network', null, null, null],
    [() => new Response('<html>private gateway details</html>', { status: 502 }), 'http', 502, null, null],
    [() => new Response('invalid'), 'contract', 200, null, null],
    [() => json({ code: '0', data: created, message: '成功' }), 'contract', 200, null, 'header-id'],
    [() => json({ code: 0, message: '成功' }), 'contract', 200, 0, 'header-id'],
    [() => json(null), 'contract', 200, null, 'header-id'],
    [() => json([]), 'contract', 200, null, 'header-id'],
    [() => json({ code: 0, message: null, data: {} }), 'contract', 200, 0, 'header-id'],
  ]
  for (const [respond, kind, status, code, requestId] of cases) {
    let calls = 0
    t.mock.method(globalThis, 'fetch', async () => { calls++; return respond() })
    await assert.rejects(api.createInterview({ mode: 'COMPREHENSIVE' }), error => {
      assert.ok(error instanceof ApiError)
      assert.equal(error.kind, kind)
      assert.equal(error.status, status)
      assert.equal(error.code, code)
      assert.equal(error.requestId, requestId)
      assert.ok(!error.message.includes('private'))
      return true
    })
    assert.equal(calls, 1)
    t.mock.restoreAll()
  }
})

test('transport unwraps null data, supports missing requestId, and cancellation', async t => {
  t.mock.method(globalThis, 'fetch', async () => json(envelope(null)))
  assert.equal(await http('/fixture'), null)
  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    code: 23001, message: '面试不存在', data: null,
  }), { status: 404 }))
  await assert.rejects(api.getInterview(20), e => e instanceof ApiError && e.requestId === null)
  t.mock.restoreAll()
  const controller = new AbortController()
  controller.abort()
  t.mock.method(globalThis, 'fetch', async () => { throw new DOMException('Aborted', 'AbortError') })
  await assert.rejects(api.getInterview(20, { signal: controller.signal }),
    e => e instanceof ApiError && e.kind === 'aborted' && e.status === null && e.code === null)
})

test('body read failures preserve available HTTP status and Header requestId', async t => {
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, status: 200,
    headers: new Headers({ 'X-Request-Id': 'header-id' }),
    json: async () => { throw new TypeError('private connection details') },
  }))
  await assert.rejects(api.getInterview(20), e => e instanceof ApiError && e.kind === 'network'
    && e.status === 200 && e.code === null && e.requestId === 'header-id'
    && !e.message.includes('private'))
})

test('Create/Get/Start unwrap data without runtime DTO schema validation', async t => {
  for (const data of [null, { id: '20' }]) {
    t.mock.method(globalThis, 'fetch', async () => json(envelope(data)))
    for (const [method, args] of [['createInterview', []], ['getInterview', ['20']], ['startInterview', ['20']]]) {
      assert.deepEqual(await api[method](...args), data)
    }
    t.mock.restoreAll()
  }
})
