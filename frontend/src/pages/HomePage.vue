<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { createInterview } from '../api/interview'
import { getProfile } from '../api/profile'
import { ApiError } from '../api/http'
import type { InterviewProfile } from '../types/profile'

import type { InterviewMode, KnowledgeArea } from '../types/interview'

const mode = ref<InterviewMode | ''>('')
const focusArea = ref<KnowledgeArea | ''>('')
const areas: KnowledgeArea[] = ['JAVA', 'CONCURRENCY', 'JVM', 'MYSQL', 'REDIS', 'MQ', 'SPRING', 'DISTRIBUTED_SYSTEMS', 'SYSTEM_DESIGN']
const profile = ref<InterviewProfile | null>(null)
const profileLoading = ref(false)
const profileError = ref<ApiError | null>(null)
const valid = computed(() => !!profile.value && !profileLoading.value && !profileError.value
  && !!mode.value && (mode.value !== 'PROJECT_DEEP_DIVE' || profile.value.projects.length > 0)
  && (mode.value !== 'TECHNICAL_FOCUS' || !!focusArea.value))
watch(mode, value => { if (value !== 'TECHNICAL_FOCUS') focusArea.value = '' }, { flush: 'sync' })
const router = useRouter()
const pending = ref(false)
const error = ref<ApiError | null>(null)
const controller = new AbortController()
onUnmounted(() => controller.abort())

async function loadProfile() {
  if (profileLoading.value || pending.value) return
  profileLoading.value = true
  profileError.value = null
  try {
    const value = await getProfile({ signal: controller.signal })
    if (!controller.signal.aborted) profile.value = value
  } catch (cause) {
    if (!controller.signal.aborted) profileError.value = cause instanceof ApiError ? cause
      : new ApiError('network', null, null, '资料加载失败，请重试', null)
  } finally {
    if (!controller.signal.aborted) profileLoading.value = false
  }
}
void loadProfile()

async function create() {
  if (pending.value || !valid.value || !mode.value) return
  pending.value = true
  error.value = null
  try {
    const interview = await createInterview(mode.value === 'TECHNICAL_FOCUS'
      ? { mode: mode.value, focusArea: focusArea.value as KnowledgeArea } : { mode: mode.value },
      { signal: controller.signal })
    if (!controller.signal.aborted) await router.push(`/interviews/${interview.id}`)
  } catch (cause) {
    if (!controller.signal.aborted) error.value = cause instanceof ApiError ? cause
      : new ApiError('network', null, null, '创建或跳转失败，请重试', null)
  } finally {
    if (!controller.signal.aborted) pending.value = false
  }
}
</script>

<template>
  <header class="page-header">
    <h1>Interview Agent</h1>
    <p class="helper-text">创建一场面试，然后在 Session 页面准备计划并启动。</p>
  </header>
  <section class="card" aria-label="Current Profile">
    <h2>当前面试资料</h2>
    <p v-if="profileLoading" role="status">正在加载 Profile…</p>
    <div v-if="profile && !profileError" class="profile-summary">
      <p>姓名：{{ profile.candidateName }}</p>
      <p>目标岗位：{{ profile.positionTitle }}</p>
      <p>项目数量：{{ profile.projects.length }}</p>
      <p v-if="!profile.projects.length" class="helper-text summary-hint">项目深挖需要先添加项目；综合面试和技术专项仍可使用。</p>
    </div>
    <div v-if="profileError" role="alert">
      <p>Profile 加载失败，暂时无法创建面试：{{ profileError.message }}</p>
      <p v-if="profileError.requestId">requestId：{{ profileError.requestId }}</p>
      <button type="button" :disabled="profileLoading" @click="loadProfile">重试加载 Profile</button>
    </div>
    <div class="action-group">
      <RouterLink class="button button-secondary" to="/profile">编辑面试资料</RouterLink>
    </div>
  </section>
  <section class="card" aria-labelledby="create-heading">
    <h2 id="create-heading">创建面试</h2>
    <div class="field">
      <label for="interview-mode">面试模式（必填）</label>
      <select id="interview-mode" :value="mode" @change="mode = ($event.target as HTMLSelectElement).value as InterviewMode" required :disabled="pending">
        <option disabled value="">请选择面试模式</option>
        <option value="COMPREHENSIVE">综合面试 · COMPREHENSIVE</option>
        <option value="PROJECT_DEEP_DIVE" :disabled="!profile || !!profileError || profile.projects.length === 0">项目深挖 · PROJECT_DEEP_DIVE</option>
        <option value="TECHNICAL_FOCUS">技术专项 · TECHNICAL_FOCUS</option>
      </select>
    </div>
    <div v-if="mode === 'TECHNICAL_FOCUS'" class="field">
      <label for="focus-area">技术方向（必填）</label>
      <select id="focus-area" :value="focusArea" @change="focusArea = ($event.target as HTMLSelectElement).value as KnowledgeArea" required :disabled="pending">
        <option disabled value="">请选择技术方向</option>
        <option v-for="area in areas" :key="area" :value="area">{{ area }}</option>
      </select>
    </div>
    <div class="action-group">
      <button class="button-primary" type="button" :disabled="pending || !valid" @click="create">
        {{ pending ? '正在创建面试…' : '创建面试' }}
      </button>
    </div>
    <div v-if="error" role="alert">
      <p>{{ error.message }}</p>
      <p v-if="error.requestId">requestId：{{ error.requestId }}</p>
    </div>
  </section>
</template>

<style scoped>
.card > h2 { margin-top: 0; }
.profile-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr)); gap: 8px 24px; }
.profile-summary p { margin-bottom: 0; }
.summary-hint { grid-column: 1 / -1; }
.field { max-width: 40rem; margin-bottom: 20px; }
.field label { margin-top: 0; }
.card > .action-group:last-child { margin-bottom: 0; }
</style>
