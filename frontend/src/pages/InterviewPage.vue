<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isBusinessId } from '../types/id'
import { useInterview } from '../composables/useInterview'

const route = useRoute()
const router = useRouter()
const interviewId = computed(() => {
  const raw = route.params.id
  return isBusinessId(raw) ? raw : null
})
const { interview, loading, pending, stale, commandError, readError, created, opening, processingTurn, waitingAnswer,
  paused, completed, canRetry, canPrepare, canStart, canAnswer, canResume, canFinish, refresh, retry, prepare, start, submit, skip, pause, resume, finish } = useInterview(interviewId)
const answerDraft = ref('')
watch(() => [interviewId.value, interview.value?.currentQuestion?.id, completed.value] as const,
  ([id, question, done], previous) => {
    if (done || (previous && (id !== previous[0] || question !== previous[1]))) {
      answerDraft.value = ''
    }
  }, { flush: 'sync' })
function sendAnswer() {
  return submit(answerDraft.value)
}
function confirmSkip() {
  if (canAnswer.value && window.confirm('跳过当前问题？未提交的回答将被丢弃。')) return skip()
}
function confirmFinish() {
  if (canFinish.value && window.confirm('结束面试？未提交的回答不会保存。')) return finish()
}
</script>

<template>
  <header class="page-header session-header">
    <h1>面试 Session</h1>
    <div class="session-meta helper-text">
      <span>Interview ID：{{ route.params.id }}</span>
      <template v-if="interview">
        <span>状态：<span class="badge">{{ interview.status }}</span></span>
        <span>阶段：<span class="badge">{{ interview.currentPhase ?? '无进行中阶段' }}</span></span>
      </template>
      <button v-if="interviewId !== null" class="button-secondary" type="button" :disabled="loading || !!pending" @click="refresh">刷新状态</button>
    </div>
  </header>
  <p v-if="interviewId === null" role="alert">Interview ID 必须是 Long 范围内的正整数十进制字符串。</p>
  <template v-else>
    <p v-if="loading && !pending" role="status">正在读取面试状态…</p>
    <div v-if="commandError" class="notice-error" role="alert">
      <p>操作失败：{{ commandError.message }}</p>
      <p v-if="commandError.requestId" class="helper-text">requestId：{{ commandError.requestId }}</p>
    </div>
    <div v-if="readError" class="notice-error" role="alert">
      <p>读取失败：{{ readError.message }}</p>
      <p v-if="readError.requestId" class="helper-text">requestId：{{ readError.requestId }}</p>
    </div>
    <p v-if="stale" class="notice notice-warning">尚未确认最新状态，已有快照可能过期。请刷新状态后再操作。</p>
    <div class="processing-feedback">
      <p v-if="pending === 'prepare'" class="processing-notice" role="status">正在生成面试计划，可能需要一些时间…</p>
      <p v-else-if="pending === 'start' || opening" class="processing-notice" role="status">正在生成第一道面试问题，可能需要一些时间…</p>
      <p v-else-if="pending === 'submit'" class="processing-notice" role="status">正在评价回答并生成下一步问题，可能需要一些时间…</p>
      <p v-else-if="pending === 'retry'" class="processing-notice" role="status">正在重新处理本轮回答，可能需要一些时间…</p>
      <p v-else-if="pending === 'skip'" class="processing-notice" role="status">正在跳过当前问题并准备下一步，可能需要一些时间…</p>
      <p v-else-if="pending" class="processing-notice" role="status">正在执行 {{ pending }}，并读取最新面试状态…</p>
    </div>
    <template v-if="interview">
      <section v-if="created" class="card lifecycle-card" aria-label="面试准备">
        <h2>准备面试</h2>
        <p v-if="interview.planPrepared === true">计划已准备，点击 Start 开始面试。</p>
        <p v-else>面试已创建，请先 Prepare 准备计划，再启动面试。</p>
        <div class="action-group">
          <button v-if="interview.planPrepared === false" class="button-primary" type="button" :disabled="!canPrepare" @click="prepare">Prepare · 准备计划</button>
          <button v-if="interview.planPrepared === true" class="button-primary" type="button" :disabled="!canStart" @click="start">Start · 开始面试</button>
        </div>
      </section>
      <section v-else-if="processingTurn" class="card lifecycle-card recovery-card" aria-label="本轮处理恢复">
        <h2>本轮处理与恢复</h2>
        <p class="notice-warning" role="status">本轮处理尚未完成。当前状态无法区分仍在处理中或等待恢复，请刷新状态确认结果，也可手动重试本轮处理，由服务端校验操作是否合法。</p>
        <p class="helper-text">重试使用服务端已保存的回答，不会重新提交草稿。</p>
        <div class="action-group">
          <button class="button-secondary" type="button" :disabled="!canRetry" @click="retry">重试本轮处理</button>
        </div>
      </section>
      <section v-else-if="(waitingAnswer || paused) && interview.currentQuestion" class="card question-card" aria-label="当前问题">
        <div class="question-heading">
          <h2>当前问题</h2>
          <span class="badge question-type">{{ interview.currentQuestion.type }}</span>
        </div>
        <div class="question-content">{{ interview.currentQuestion.content }}</div>
        <p class="helper-text question-id">Question ID：{{ interview.currentQuestion.id }}</p>
        <div class="answer-area">
          <label class="answer-heading" for="answer-draft">回答</label>
          <textarea id="answer-draft" v-model="answerDraft" :readonly="paused || !canAnswer" rows="8" />
        </div>
        <template v-if="waitingAnswer">
          <div class="action-group answer-actions">
            <button class="button-primary" type="button" :disabled="!canAnswer || !answerDraft.trim()" @click="sendAnswer">Submit · 提交回答</button>
            <button class="button-secondary" type="button" :disabled="!canAnswer" @click="confirmSkip">Skip · 跳过</button>
          </div>
          <div class="action-group session-controls" aria-label="会话控制">
            <button class="button-secondary" type="button" :disabled="!canAnswer" @click="pause">Pause · 暂停</button>
            <button class="button-danger" type="button" :disabled="!canFinish" @click="confirmFinish">Finish · 结束面试</button>
          </div>
        </template>
        <template v-else>
          <p class="notice notice-warning">面试已暂停，当前草稿保留；Resume 后可继续编辑。</p>
          <div class="action-group session-controls" aria-label="会话控制">
            <button class="button-secondary" type="button" :disabled="!canResume" @click="resume">Resume · 恢复</button>
            <button class="button-danger" type="button" :disabled="!canFinish" @click="confirmFinish">Finish · 结束面试</button>
          </div>
        </template>
      </section>
      <section v-else-if="completed" class="card lifecycle-card" aria-label="面试完成">
        <h2>面试已完成</h2>
        <p class="notice notice-success">面试已完成。</p>
        <div class="action-group">
          <button class="button-primary" type="button" @click="router.push(`/interviews/${interviewId}/review`)">查看面试复盘</button>
        </div>
      </section>
      <p v-else-if="!opening" class="notice notice-warning">此状态暂不支持操作，或缺少有效当前题目。请刷新状态。</p>
    </template>
  </template>
</template>

<style scoped>
.session-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 20px; }
.session-meta > * { min-width: 0; max-width: 100%; }
.session-meta button { font-size: 0.875rem; }
.processing-notice { border-inline-start: 4px solid var(--color-primary); font-weight: 600; }
.lifecycle-card > h2 { margin-top: 0; }
.lifecycle-card > .action-group:last-child { margin-bottom: 0; }
.question-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 20px; }
.question-heading h2 { margin: 0; }
.question-type { color: var(--color-primary); background: #eef4fd; border-color: #b9cde9; }
.question-content { padding: clamp(16px, 3vw, 24px); border: 1px solid var(--color-border); border-radius: var(--radius); background: #f6f8fb; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 1.125rem; line-height: 1.8; }
.question-id { margin-block: 8px 24px; }
.answer-area { margin-top: 24px; }
.answer-heading { margin-block: 0 12px; font-size: 1.125rem; font-weight: 650; }
textarea { display: block; width: 100%; box-sizing: border-box; }
.session-controls { padding-top: 20px; border-top: 1px solid var(--color-border); margin-bottom: 0; }
</style>
