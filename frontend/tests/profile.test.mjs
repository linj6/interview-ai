import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextTick } from 'vue'
import { mount, flatten, text, button, settle, deferred, json, profile, created } from './ui-helpers.mjs'

const field = (ui, id) => flatten(ui.root).find(n => n.props.id === id)
async function input(ui, id, value) {
  field(ui, id).props.onInput({ target: { value } }); await nextTick()
}
const submit = ui => flatten(ui.root).find(n => n.tag === 'form').props.onSubmit({ preventDefault() {} })
const choose = async (ui, id, value) => {
  field(ui, id).props.onChange({ target: { value } }); await settle()
}
const failure = () => new Response(JSON.stringify({ code: 10001, message: '保存被拒绝', data: null, requestId: 'profile-error' }), { status: 400 })

test('Profile GET renders fields and guidance; loading error retries', async t => {
  const read = deferred(); let calls = 0
  t.mock.method(globalThis, 'fetch', async path => {
    assert.equal(path, '/api/interview-profile')
    if (++calls === 1) return read.promise
    return json(profile())
  })
  const ui = await mount('ProfilePage'); t.after(() => ui.app.unmount())
  assert.match(text(ui.root), /正在加载 Profile/)
  read.resolve(failure()); await settle()
  assert.match(text(ui.root), /profile-error/)
  await button(ui.root, '重试加载 Profile').props.onClick(); await settle()
  assert.equal(field(ui, 'candidate-name').props.value, '候选人')
  assert.equal(field(ui, 'resume-summary').props.value, '简历摘要')
  assert.equal(field(ui, 'position-title').props.value, 'Java 工程师')
  assert.equal(field(ui, 'position-description').props.value, '岗位描述')
  assert.equal(field(ui, 'project-name-0').props.value, '现有项目')
  assert.equal(field(ui, 'project-description-0').props.value, '项目描述')
  assert.match(text(ui.root), /首次使用时，请先将示例内容替换为你的真实资料/)
  assert.match(text(ui.root), /资料修改会影响正在进行中的面试/)
  assert.equal(button(ui.root, '保存 Profile').props.disabled, true)
})

test('Projects add and delete locally from zero to five; sixth addition is blocked', async t => {
  let calls = 0
  t.mock.method(globalThis, 'fetch', async () => { calls++; return json(profile([])) })
  const ui = await mount('ProfilePage'); t.after(() => ui.app.unmount()); await settle()
  assert.match(text(ui.root), /尚未添加项目/)
  for (let i = 0; i < 5; i++) { await button(ui.root, '添加项目').props.onClick(); await nextTick() }
  assert.equal(button(ui.root, '添加项目').props.disabled, true)
  await button(ui.root, '添加项目').props.onClick(); await nextTick()
  assert.equal(flatten(ui.root).filter(n => n.tag === 'input' && n.props.id.startsWith('project-name')).length, 5)
  assert.match(text(ui.root), /最多添加 5 个项目/)
  for (let i = 0; i < 5; i++) { await button(ui.root, '删除项目 1').props.onClick(); await nextTick() }
  assert.match(text(ui.root), /尚未添加项目/)
  assert.equal(button(ui.root, '添加项目').props.disabled, false)
  assert.equal(button(ui.root, '保存 Profile').props.disabled, true)
  assert.equal(calls, 1)
})

test('PUT replaces all fields, drops deleted IDs, blocks duplicates and adopts exact canonical new IDs without GET', async t => {
  const response = deferred(); const calls = []; const bodies = []
  const largeId = '1975511234567890123'
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    calls.push([path, init.method])
    if (init.method === 'GET') return json(profile([{ id: '101', name: '保留项目', description: '' }, { id: '102', name: '删除项目', description: '' }]))
    bodies.push(init.body)
    if (bodies.length === 1) return response.promise
    return new Response(`{"code":0,"message":"成功","data":{"candidateName":"真实姓名","resumeSummary":"新摘要","positionTitle":"新岗位","positionDescription":"新描述","projects":[{"id":"101","name":"编辑项目","description":"原项目描述"},{"id":"${largeId}","name":"再次编辑","description":"新项目描述"}]}}`)
  })
  const ui = await mount('ProfilePage'); t.after(() => ui.app.unmount()); await settle()
  await button(ui.root, '删除项目 2').props.onClick(); await nextTick()
  await button(ui.root, '添加项目').props.onClick(); await nextTick()
  for (const [id, value] of [['candidate-name', '真实姓名'], ['resume-summary', '新摘要'], ['position-title', '新岗位'], ['position-description', '新描述'],
    ['project-name-0', '编辑项目'], ['project-description-0', '原项目描述'], ['project-name-1', '新项目'], ['project-description-1', '新项目描述']]) await input(ui, id, value)
  assert.match(text(ui.root), /有未保存的修改/)
  const first = submit(ui); await nextTick()
  assert.equal(button(ui.root, '正在保存 Profile').props.disabled, true)
  assert.equal(flatten(ui.root).find(n => n.tag === 'fieldset').props.disabled, true)
  await submit(ui); assert.equal(bodies.length, 1)
  assert.deepEqual(JSON.parse(bodies[0]), {
    candidateName: '真实姓名', resumeSummary: '新摘要', positionTitle: '新岗位', positionDescription: '新描述',
    projects: [{ id: '101', name: '编辑项目', description: '原项目描述' }, { id: null, name: '新项目', description: '新项目描述' }],
  })
  response.resolve(new Response(`{"code":0,"message":"成功","data":{"candidateName":"真实姓名","resumeSummary":"新摘要","positionTitle":"新岗位","positionDescription":"新描述","projects":[{"id":"101","name":"编辑项目","description":"原项目描述"},{"id":"${largeId}","name":"服务端规范名称","description":"新项目描述"}]}}`))
  await first; await settle()
  assert.match(text(ui.root), /Profile 保存成功/)
  assert.equal(field(ui, 'project-name-1').props.value, '服务端规范名称')
  assert.equal(button(ui.root, '保存 Profile').props.disabled, true)
  await input(ui, 'project-name-1', '再次编辑'); await submit(ui); await settle()
  assert.ok(bodies[1].includes(`"id":"${largeId}"`))
  assert.equal(JSON.parse(bodies[1]).projects[1].id, largeId)
  assert.deepEqual(calls.map(c => c[1]), ['GET', 'PUT', 'PUT'])
  assert.ok(calls.every(c => c[0] === '/api/interview-profile'))
})

test('validation prevents PUT; failed save preserves draft and permits manual retry', async t => {
  let writes = 0
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'GET') return json(profile([]))
    if (++writes === 1) return failure()
    return json({ ...profile([]), candidateName: '真实姓名', resumeSummary: '' })
  })
  const ui = await mount('ProfilePage'); t.after(() => ui.app.unmount()); await settle()
  await input(ui, 'candidate-name', '  '); await submit(ui); await settle()
  assert.match(text(ui.root), /姓名不能为空/); assert.equal(writes, 0)
  await input(ui, 'candidate-name', '真实姓名')
  await input(ui, 'resume-summary', '中'.repeat(21846)); await submit(ui); await settle()
  assert.match(text(ui.root), /UTF-8 字节/); assert.equal(writes, 0)
  await button(ui.root, '添加项目').props.onClick(); await nextTick()
  await input(ui, 'resume-summary', ''); await submit(ui); await settle()
  assert.match(text(ui.root), /项目名称不能为空/); assert.equal(writes, 0)
  await button(ui.root, '删除项目 1').props.onClick(); await nextTick()
  await submit(ui); await settle()
  assert.match(text(ui.root), /Profile 保存失败.*保存被拒绝/)
  assert.match(text(ui.root), /profile-error|有未保存的修改/)
  assert.equal(field(ui, 'candidate-name').props.value, '真实姓名')
  assert.equal(button(ui.root, '保存 Profile').props.disabled, false)
  await submit(ui); await settle()
  assert.match(text(ui.root), /Profile 保存成功/); assert.equal(writes, 2)
})

test('Home summarizes Profile without long text; zero projects blocks only deep dive', async t => {
  const bodies = []
  t.mock.method(globalThis, 'fetch', async (path, init) => {
    if (path === '/api/interview-profile') return json(profile([]))
    bodies.push(JSON.parse(init.body)); return json(created('42'))
  })
  const ui = await mount('HomePage'); t.after(() => ui.app.unmount()); await settle()
  assert.match(text(ui.root), /姓名：候选人.*目标岗位：Java 工程师.*项目数量：0/)
  assert.doesNotMatch(text(ui.root), /简历摘要|岗位描述/)
  assert.ok(flatten(ui.root).some(n => n.tag === 'a' && n.props.href === '/profile'))
  assert.equal(flatten(ui.root).find(n => n.tag === 'option' && n.props.value === 'PROJECT_DEEP_DIVE').props.disabled, true)
  await choose(ui, 'interview-mode', 'PROJECT_DEEP_DIVE')
  await button(ui.root, '创建面试').props.onClick(); assert.equal(bodies.length, 0)
  assert.match(text(ui.root), /需要先添加项目/)
  await choose(ui, 'interview-mode', 'COMPREHENSIVE')
  assert.equal(button(ui.root, '创建面试').props.disabled, false)
  await button(ui.root, '创建面试').props.onClick(); await settle()
  await choose(ui, 'interview-mode', 'TECHNICAL_FOCUS'); await choose(ui, 'focus-area', 'JAVA')
  assert.equal(button(ui.root, '创建面试').props.disabled, false)
  await button(ui.root, '创建面试').props.onClick(); await settle()
  assert.deepEqual(bodies, [{ mode: 'COMPREHENSIVE' }, { mode: 'TECHNICAL_FOCUS', focusArea: 'JAVA' }])
})

test('Home Profile loading and failure prohibit create; retry restores availability', async t => {
  const read = deferred(); let reads = 0; let writes = 0
  t.mock.method(globalThis, 'fetch', async (path) => {
    if (path !== '/api/interview-profile') { writes++; return json(created('42')) }
    return ++reads === 1 ? read.promise : json(profile())
  })
  const ui = await mount('HomePage'); t.after(() => ui.app.unmount())
  await choose(ui, 'interview-mode', 'COMPREHENSIVE')
  assert.equal(button(ui.root, '创建面试').props.disabled, true)
  read.resolve(failure()); await settle()
  assert.match(text(ui.root), /Profile 加载失败，暂时无法创建面试/)
  await button(ui.root, '创建面试').props.onClick(); assert.equal(writes, 0)
  assert.ok(flatten(ui.root).some(n => n.tag === 'a' && n.props.href === '/profile'))
  await button(ui.root, '重试加载 Profile').props.onClick(); await settle()
  assert.equal(button(ui.root, '创建面试').props.disabled, false)
  assert.equal(flatten(ui.root).find(n => n.tag === 'option' && n.props.value === 'PROJECT_DEEP_DIVE').props.disabled, false)
})

test('Profile transport preserves existing Long IDs and leaves JSON-looking description text unchanged', async t => {
  const largeId = '1975511234567890123'
  const description = '原文："id":123 和 "id":"456"'
  const source = JSON.stringify(profile([{ id: largeId, name: '现有项目', description }]))
  let body
  t.mock.method(globalThis, 'fetch', async (_path, init) => {
    if (init.method === 'PUT') body = init.body
    return new Response(`{"code":0,"message":"成功","data":${source}}`)
  })
  const ui = await mount('ProfilePage'); t.after(() => ui.app.unmount()); await settle()
  assert.equal(field(ui, 'project-description-0').props.value, description)
  await input(ui, 'candidate-name', '修改姓名'); await submit(ui); await settle()
  assert.ok(body.includes(`"id":"${largeId}"`))
  assert.equal(JSON.parse(body).projects[0].description, description)
  assert.match(text(ui.root), /Profile 保存成功/)
})
