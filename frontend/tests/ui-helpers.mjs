import { readFile } from 'node:fs/promises'
import { compileScript, parse } from '@vue/compiler-sfc'
import { createRenderer, nextTick } from 'vue'
import ts from 'typescript'

globalThis.ShadowRoot ??= class ShadowRoot {}
globalThis.Document ??= class Document {}
globalThis.window ??= { confirm: () => false }
const url = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const compile = source => ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const vueUrl = import.meta.resolve('vue')
const httpUrl = url(compile(await readFile(new URL('../src/api/http.ts', import.meta.url), 'utf8')))
const apiUrl = url(compile(await readFile(new URL('../src/api/interview.ts', import.meta.url), 'utf8'))
  .replaceAll("'./http'", JSON.stringify(httpUrl)))
const idUrl = url(compile(await readFile(new URL('../src/types/id.ts', import.meta.url), 'utf8')))
const profileApiUrl = url(compile(await readFile(new URL('../src/api/profile.ts', import.meta.url), 'utf8'))
  .replaceAll("'./http'", JSON.stringify(httpUrl))
  .replaceAll("'../types/id'", JSON.stringify(idUrl)))
const composableUrl = url(compile(await readFile(new URL('../src/composables/useInterview.ts', import.meta.url), 'utf8'))
  .replaceAll("'vue'", JSON.stringify(vueUrl))
  .replaceAll("'../api/interview'", JSON.stringify(apiUrl))
  .replaceAll("'../api/http'", JSON.stringify(httpUrl)))
let sequence = 0
const node = (text = '') => ({ text, children: [], props: {}, parent: null, value: '', listeners: {},
  addEventListener(event, handler) { this.listeners[event] = handler },
  getRootNode() { return {} },
})
const renderer = createRenderer({
  createElement: tag => ({ ...node(), tag }), createText: node, createComment: () => node(),
  setText: (n, text) => { n.text = text },
  setElementText: (n, text) => { n.text = text; n.children = [] },
  patchProp: (n, key, _old, value) => { n.props[key] = value },
  insert(n, parent, anchor) {
    if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1)
    n.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, n)
  },
  remove(n) { n.parent.children.splice(n.parent.children.indexOf(n), 1); n.parent = null },
  parentNode: n => n.parent,
  nextSibling: n => n.parent?.children[n.parent.children.indexOf(n) + 1] ?? null,
})
const flatten = n => [n, ...n.children.flatMap(flatten)]
const text = root => flatten(root).map(n => n.text).join(' ')
const button = (root, label) => flatten(root).find(n => n.tag === 'button' && n.text.includes(label))
const settle = async () => { for (let i = 0; i < 15; i++) { await new Promise(resolve => setImmediate(resolve)); await nextTick() } }
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const created = id => ({ id, mode: 'COMPREHENSIVE', focusArea: null, planPrepared: true, status: 'CREATED', currentPhase: null, firstQuestion: null, currentQuestion: null, expiresAt: null })
const opening = id => ({ ...created(id), status: 'IN_PROGRESS', currentPhase: 'OPENING' })
const waiting = id => ({ ...opening(id), currentPhase: 'WAITING_ANSWER',
  currentQuestion: { id: '31', type: 'MAIN', status: 'WAITING_ANSWER', content: '当前问题内容' } })
const json = data => new Response(JSON.stringify({ code: 0, message: '成功', data, requestId: 'fixture-id' }))
const profile = (projects = [{ id: '101', name: '现有项目', description: '项目描述' }]) => ({
  candidateName: '候选人', resumeSummary: '简历摘要', projects,
  positionTitle: 'Java 工程师', positionDescription: '岗位描述',
})

async function mount(page, id = '20') {
  const routerUrl = url(`import { reactive, h } from ${JSON.stringify(vueUrl)};
    export const RouterLink = {props:['to'], setup: (props, {slots}) => () => h('a', {href:props.to}, slots.default?.())};
    export const route = reactive({params:{id:${JSON.stringify(id)}}});
    export const paths = []; export const useRoute = () => route;
    export const useRouter = () => ({push: async path => paths.push(path)}); // ${sequence++}`)
  const source = await readFile(new URL(`../src/pages/${page}.vue`, import.meta.url), 'utf8')
  const script = compileScript(parse(source).descriptor, { id: `test-${sequence}`, inlineTemplate: true }).content
  const component = await import(url(compile(script)
    .replaceAll("'../composables/useInterview'", JSON.stringify(composableUrl))
    .replaceAll(/(['"])vue\1/g, JSON.stringify(vueUrl))
    .replaceAll(/(['"])vue-router\1/g, JSON.stringify(routerUrl))
    .replaceAll(/(['"])\.\.\/api\/interview\1/g, JSON.stringify(apiUrl))
    .replaceAll(/(['"])\.\.\/api\/profile\1/g, JSON.stringify(profileApiUrl))
    .replaceAll(/(['"])\.\.\/types\/id\1/g, JSON.stringify(idUrl))
    .replaceAll(/(['"])\.\.\/api\/http\1/g, JSON.stringify(httpUrl))))
  const root = node()
  const app = renderer.createApp(component.default)
  app.mount(root)
  return { root, app, ...await import(routerUrl) }
}


export { mount, flatten, text, button, settle, deferred, created, opening, waiting, json, profile }
