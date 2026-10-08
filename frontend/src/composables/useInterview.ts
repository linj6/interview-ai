import { computed, onScopeDispose, ref, watch } from 'vue'
import type { Ref } from 'vue'
import { getInterview, prepareInterview, startInterview, submitAnswer, skipQuestion, pauseInterview, resumeInterview, finishInterview, retryTurn } from '../api/interview'
import { ApiError } from '../api/http'
import type { InterviewDetailResponse } from '../types/interview'

type Command = 'prepare' | 'start' | 'submit' | 'skip' | 'pause' | 'resume' | 'finish' | 'retry'

export function useInterview(interviewId: Ref<string | null>) {
  const interview = ref<InterviewDetailResponse | null>(null)
  const loading = ref(false)
  const pending = ref<Command | null>(null)
  const stale = ref(false)
  const commandError = ref<ApiError | null>(null)
  const readError = ref<ApiError | null>(null)
  let controller = new AbortController()
  const ready = computed(() => !loading.value && !pending.value && !stale.value)
  const created = computed(() => interview.value?.status === 'CREATED' && interview.value.currentPhase === null)
  const opening = computed(() => interview.value?.status === 'IN_PROGRESS' && interview.value.currentPhase === 'OPENING')
  const processingTurn = computed(() => interview.value?.status === 'IN_PROGRESS' && interview.value.currentPhase === 'PROCESSING_TURN')
  const hasQuestion = computed(() => interview.value?.currentQuestion?.status === 'WAITING_ANSWER')
  const waitingAnswer = computed(() => interview.value?.status === 'IN_PROGRESS' && interview.value.currentPhase === 'WAITING_ANSWER' && hasQuestion.value)
  const paused = computed(() => interview.value?.status === 'PAUSED' && interview.value.currentPhase === 'WAITING_ANSWER' && hasQuestion.value)
  const completed = computed(() => interview.value?.status === 'COMPLETED' && interview.value.currentPhase === null)
  const canRetry = computed(() => ready.value && processingTurn.value)
  const canPrepare = computed(() => ready.value && created.value && interview.value?.planPrepared === false)
  const canStart = computed(() => ready.value && created.value && interview.value?.planPrepared === true)
  const canAnswer = computed(() => ready.value && waitingAnswer.value)
  const canResume = computed(() => ready.value && paused.value)
  const canFinish = computed(() => ready.value && (waitingAnswer.value || paused.value))

  function asError(cause: unknown): ApiError {
    return cause instanceof ApiError ? cause : new ApiError('network', null, null, '请求失败，请重新读取状态', null)
  }
  async function load(id: string, signal: AbortSignal, preserveCommandError = false) {
    loading.value = true
    readError.value = null
    try {
      const snapshot = await getInterview(id, { signal })
      if (signal.aborted) return
      if (snapshot.id !== id) throw new ApiError('contract', null, null, '服务器返回的 Interview ID 不匹配', null)
      if (typeof snapshot.planPrepared !== 'boolean') throw new ApiError('contract', null, null, '服务器未返回有效的计划准备状态，请重新读取', null)
      interview.value = snapshot
      if (!preserveCommandError) commandError.value = null
      stale.value = false
    } catch (cause) {
      if (signal.aborted) return
      readError.value = asError(cause)
      stale.value = true
    } finally {
      if (!signal.aborted) loading.value = false
    }
  }
  async function refresh() {
    if (interviewId.value === null || loading.value || pending.value) return
    await load(interviewId.value, controller.signal)
  }
  async function command(name: Command, allowed: boolean, send: (id: string, signal: AbortSignal) => Promise<unknown>) {
    if (!allowed || interviewId.value === null) return false
    const id = interviewId.value
    const signal = controller.signal
    pending.value = name
    commandError.value = null
    let succeeded = false
    try {
      await send(id, signal)
      succeeded = !signal.aborted
    } catch (cause) {
      if (!signal.aborted) commandError.value = asError(cause)
    } finally {
      // Every command outcome is reconciled through GET, never its response DTO.
      if (!signal.aborted) {
        const failure = commandError.value
        await load(id, signal, true)
        if (!signal.aborted && name === 'prepare' && !stale.value && created.value && interview.value?.planPrepared === false) {
          commandError.value = failure ?? new ApiError('contract', null, null, '尚未确认计划已准备，请重试 Prepare', null)
        }
        if (!signal.aborted) pending.value = null
      }
    }
    return succeeded && !signal.aborted
  }
  // Retry explicitly recovers the persisted Turn; it never replays Submit or sends a draft.
  const retry = () => command('retry', canRetry.value, (id, signal) => retryTurn(id, { signal }))
  const prepare = () => command('prepare', canPrepare.value, (id, signal) => prepareInterview(id, { signal }))
  const start = () => command('start', canStart.value, (id, signal) => startInterview(id, { signal }))
  const pause = () => command('pause', canAnswer.value, (id, signal) => pauseInterview(id, { signal }))
  const resume = () => command('resume', canResume.value, (id, signal) => resumeInterview(id, { signal }))
  const finish = () => command('finish', canFinish.value, (id, signal) => finishInterview(id, { signal }))
  function submit(content: string) {
    const questionId = interview.value?.currentQuestion?.id
    return command('submit', canAnswer.value && !!content.trim() && questionId !== undefined,
      (id, signal) => submitAnswer(id, questionId!, { content }, { signal }))
  }
  function skip() {
    const questionId = interview.value?.currentQuestion?.id
    return command('skip', canAnswer.value && questionId !== undefined,
      (id, signal) => skipQuestion(id, questionId!, { signal }))
  }
  watch(interviewId, id => {
    controller.abort()
    controller = new AbortController()
    interview.value = null
    loading.value = false
    pending.value = null
    stale.value = false
    commandError.value = null
    readError.value = null
    if (id !== null) void load(id, controller.signal)
  }, { immediate: true })
  onScopeDispose(() => controller.abort())
  return { interview, loading, pending, stale, commandError, readError, created, opening, processingTurn, waitingAnswer, paused,
    completed, canRetry, canPrepare, canStart, canAnswer, canResume, canFinish, refresh, retry, prepare, start, submit, skip, pause, resume, finish }
}
