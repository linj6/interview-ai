<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isBusinessId } from '../types/id'
import { getReview } from '../api/interview'
import { ApiError } from '../api/http'
import type { InterviewReviewResponse } from '../types/interview'
const route = useRoute()
const router = useRouter()
const interviewId = computed(() => {
  const raw = route.params.id
  return isBusinessId(raw) ? raw : null
})
const review = ref<InterviewReviewResponse | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)
let controller = new AbortController()
async function load() {
  const id = interviewId.value
  if (id === null || loading.value) return
  const signal = controller.signal
  loading.value = true
  review.value = null
  error.value = null
  try {
    const result = await getReview(id, { signal })
    if (signal.aborted) return
    if (!result || result.interviewId !== id || !result.overview || !Array.isArray(result.questions)) {
      throw new ApiError('contract', null, null, '服务器返回的 Review 数据不完整或 Interview ID 不匹配', null)
    }
    review.value = result
  } catch (cause) {
    if (!signal.aborted) error.value = cause instanceof ApiError ? cause
      : new ApiError('contract', null, null, '无法读取面试复盘，请重新加载', null)
  } finally {
    if (!signal.aborted) loading.value = false
  }
}
watch(interviewId, () => {
  controller.abort()
  controller = new AbortController()
  loading.value = false
  review.value = null
  error.value = null
  void load()
}, { immediate: true })
onScopeDispose(() => controller.abort())
</script>

<template>
  <header class="page-header">
    <h1>面试复盘</h1>
    <div class="review-meta helper-text">
      <span>Interview ID：{{ route.params.id }}</span>
      <button v-if="interviewId !== null" class="button-secondary" type="button" :disabled="loading" @click="load">重新加载复盘</button>
    </div>
    <div class="action-group">
      <button class="button-secondary" type="button" @click="router.push('/')">返回首页</button>
      <button v-if="interviewId !== null" class="button-secondary" type="button" @click="router.push(`/interviews/${interviewId}`)">返回 Session</button>
    </div>
  </header>
  <p v-if="interviewId === null" role="alert">Interview ID 必须是 Long 范围内的正整数十进制字符串。</p>
  <template v-else>
    <p v-if="loading" role="status">正在读取面试复盘…</p>
    <div v-if="error" role="alert">
      <p>读取失败：{{ error.message }}</p>
      <p v-if="error.requestId" class="helper-text">requestId：{{ error.requestId }}</p>
    </div>
    <template v-if="review">
      <section class="card overview-card" aria-label="Overview">
        <h2>Overview · 面试概览</h2>
        <div class="summary-stats">
          <p><strong>时长：{{ review.overview.durationSeconds }} 秒</strong></p>
          <p><span class="helper-text">题目数量</span><strong>MAIN：{{ review.overview.mainQuestionCount }}；FOLLOW_UP：{{ review.overview.followUpQuestionCount }}</strong></p>
        </div>
        <p class="result-summary helper-text">ANSWERED：{{ review.overview.answeredCount }}；SKIPPED：{{ review.overview.skippedCount }}；UNANSWERED：{{ review.overview.unansweredCount }}</p>
        <div class="overview-times helper-text">
          <p>开始时间：{{ review.overview.startedAt }}</p>
          <p>结束时间：{{ review.overview.completedAt }}</p>
        </div>
      </section>
      <p v-if="!review.questions.length" class="helper-text">暂无问题记录。</p>
      <section v-for="main in review.questions" :key="main.questionId" class="card question-chain" aria-label="MAIN 问题链">
        <template v-for="(question, index) in [main, ...main.followUps]" :key="question.questionId">
          <article :class="index === 0 ? 'main-question' : 'follow-up-question'" :aria-label="index === 0 ? 'MAIN' : 'FOLLOW_UP'">
            <div class="question-heading">
              <h3>{{ index === 0 ? '主问题' : '追问' }}</h3>
              <span class="badge question-type">{{ index === 0 ? 'MAIN' : 'FOLLOW_UP' }}</span>
              <span class="badge result-badge">{{ question.result }}</span>
            </div>
            <p class="helper-text question-id">Question {{ question.questionId }}</p>
            <div class="question-content content">{{ question.content }}</div>
            <section class="answer-section" aria-label="用户回答">
              <h4>用户回答</h4>
              <p :class="['content', 'answer-content', { 'helper-text': question.answer === null }]">{{ question.answer ?? '无回答记录' }}</p>
            </section>
            <section v-if="question.evaluation" class="evaluation-section" aria-label="Evaluation">
              <div class="evaluation-heading">
                <h4>Evaluation · 回答评价</h4>
                <p class="evaluation-score">总分：<strong>{{ question.evaluation.overallScore }}</strong></p>
              </div>
              <div class="feedback-grid">
                <section aria-label="优点">
                  <h5>优点</h5>
                  <p class="content">{{ question.evaluation.strengths.join('；') || '无' }}</p>
                </section>
                <section aria-label="不足">
                  <h5>不足</h5>
                  <p class="content">{{ question.evaluation.weaknesses.join('；') || '无' }}</p>
                </section>
              </div>
              <section class="evaluation-detail" aria-label="遗漏要点">
                <h5>遗漏要点</h5>
                <p v-if="!question.evaluation.missingPoints.length" class="helper-text">无</p>
                <ul class="detail-list"><li v-for="(point, i) in question.evaluation.missingPoints" :key="i">
                  <strong>{{ point.description }}</strong><span class="badge importance-badge">{{ point.importance }}</span>
                  <p class="content">{{ point.explanation }}</p>
                </li></ul>
              </section>
              <section class="evaluation-detail" aria-label="维度评分">
                <h5>维度评分</h5>
                <ul class="detail-list"><li v-for="(dimension, i) in question.evaluation.dimensionScores" :key="i">
                  <div class="dimension-heading"><strong>{{ dimension.name }}</strong><span class="badge">{{ dimension.score }}</span></div>
                  <p class="content helper-text">{{ dimension.explanation }}</p>
                </li></ul>
              </section>
              <section v-if="question.evaluation.referenceAnswer" class="reference-answer" aria-label="AI 参考回答">
                <h5>参考回答 · {{ question.evaluation.referenceAnswer.answerForm }}</h5>
                <p class="helper-text">AI 生成的参考内容，供学习与组织回答使用，不是你的实际回答，也不代表真实项目事实。</p>
                <ul><li v-for="(point, i) in question.evaluation.referenceAnswer.keyPoints" :key="i">{{ point }}</li></ul>
                <p class="content">{{ question.evaluation.referenceAnswer.content }}</p>
              </section>
            </section>
            <p v-else class="helper-text missing-evaluation">无评价记录。</p>
          </article>
        </template>
      </section>
    </template>
  </template>
</template>

<style scoped>
.review-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 20px; }
.review-meta > * { min-width: 0; max-width: 100%; }
.review-meta button { font-size: 0.875rem; }
.overview-card > h2 { margin-top: 0; }
.summary-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 16px 32px; }
.summary-stats p { margin: 0; }
.summary-stats span, .summary-stats strong { display: block; }
.summary-stats strong { margin-top: 4px; font-size: 1.25rem; color: #1b2b42; }
.result-summary { margin-block: 20px 16px; }
.overview-times { display: flex; flex-wrap: wrap; gap: 8px 24px; padding-top: 16px; border-top: 1px solid var(--color-border); }
.overview-times p { margin: 0; }
.question-heading, .evaluation-heading, .dimension-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; }
.question-heading h3, .evaluation-heading h4 { margin: 0; }
.question-type { color: var(--color-primary); background: #eef4fd; border-color: #b9cde9; }
.result-badge { font-weight: 400; }
.question-id { margin-block: 8px 16px; }
.content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
.question-content { padding: clamp(16px, 3vw, 24px); background: #f6f8fb; border: 1px solid var(--color-border); border-radius: var(--radius); font-size: 1.0625rem; }
.answer-section, .evaluation-section { margin-top: 24px; }
.answer-section h4 { margin-top: 0; }
.answer-content { padding-inline-start: 16px; border-inline-start: 3px solid var(--color-border); }
.evaluation-section { padding-top: 24px; border-top: 1px solid var(--color-border); }
.evaluation-heading { justify-content: space-between; margin-bottom: 20px; }
.evaluation-score { margin: 0; color: var(--color-primary); }
.evaluation-score strong { font-size: 1.75rem; }
.feedback-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 16px 32px; }
h5 { margin: 0 0 8px; font-size: 0.9375rem; line-height: 1.5; color: #1b2b42; }
.feedback-grid p { margin-bottom: 0; }
.evaluation-detail { margin-top: 24px; }
.detail-list { padding-inline-start: 24px; margin-block: 8px 0; }
.detail-list li { padding-block: 8px; }
.detail-list p { margin-block: 4px 0; }
.importance-badge { margin-inline-start: 8px; }
.reference-answer { margin-top: 24px; padding: clamp(16px, 3vw, 24px); background: #eef4fd; border-inline-start: 3px solid #b9cde9; border-radius: var(--radius); }
.reference-answer > :last-child { margin-bottom: 0; }
.follow-up-question { margin-top: 32px; padding-top: 24px; padding-inline-start: clamp(12px, 2vw, 24px); border-top: 1px solid var(--color-border); border-inline-start: 3px solid var(--color-border); }
.missing-evaluation { margin-block: 20px 0; }
</style>
