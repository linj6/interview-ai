<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getProfile, saveProfile, validateProfile } from '../api/profile'
import { ApiError } from '../api/http'
import type { InterviewProfile } from '../types/profile'

const profile = ref<InterviewProfile | null>(null)
const baseline = ref('')
const loading = ref(false)
const pending = ref(false)
const loadError = ref<ApiError | null>(null)
const saveError = ref<ApiError | null>(null)
const validationError = ref<string | null>(null)
const saved = ref(false)
const dirty = computed(() => profile.value !== null && JSON.stringify(profile.value) !== baseline.value)
const controller = new AbortController()
onUnmounted(() => controller.abort())

function adopt(value: InterviewProfile) {
  profile.value = value
  baseline.value = JSON.stringify(value)
}

async function load() {
  if (loading.value) return
  loading.value = true
  loadError.value = null
  try {
    const value = await getProfile({ signal: controller.signal })
    if (!controller.signal.aborted) adopt(value)
  } catch (cause) {
    if (!controller.signal.aborted) loadError.value = cause instanceof ApiError ? cause
      : new ApiError('network', null, null, '资料加载失败，请重试', null)
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

function addProject() {
  if (!profile.value || pending.value || profile.value.projects.length >= 5) return
  profile.value.projects.push({ id: null, name: '', description: '' })
}

function removeProject(index: number) {
  if (!pending.value) profile.value?.projects.splice(index, 1)
}

async function save() {
  if (pending.value || !profile.value || !dirty.value) return
  saved.value = false
  saveError.value = null
  validationError.value = validateProfile(profile.value)
  if (validationError.value) return
  pending.value = true
  try {
    const value = await saveProfile(profile.value, { signal: controller.signal })
    if (!controller.signal.aborted) { adopt(value); saved.value = true }
  } catch (cause) {
    if (!controller.signal.aborted) saveError.value = cause instanceof ApiError ? cause
      : new ApiError('network', null, null, '保存失败，请重试', null)
  } finally {
    if (!controller.signal.aborted) pending.value = false
  }
}

void load()
</script>

<template>
  <header class="page-header">
    <h1>面试资料</h1>
    <p class="helper-text">面试问题和评估会基于这里的资料生成。首次使用时，请先将示例内容替换为你的真实资料。</p>
    <div class="action-group">
      <RouterLink class="button button-secondary" to="/">返回首页</RouterLink>
    </div>
  </header>
  <p class="notice notice-warning">资料修改会影响正在进行中的面试，建议在开始新面试前完成编辑。</p>
  <p v-if="loading" role="status">正在加载 Profile…</p>
  <div v-if="loadError" role="alert">
    <p>{{ loadError.message }}</p>
    <p v-if="loadError.requestId">requestId：{{ loadError.requestId }}</p>
    <button type="button" :disabled="loading" @click="load">重试加载 Profile</button>
  </div>
  <form v-if="profile" @submit.prevent="save">
    <fieldset class="profile-fields" :disabled="pending">
      <legend>面试资料</legend>
      <section class="card" aria-labelledby="candidate-heading">
        <h2 id="candidate-heading">个人资料</h2>
        <label for="candidate-name">姓名</label>
        <input id="candidate-name" :value="profile.candidateName" @input="profile.candidateName = ($event.target as HTMLInputElement).value" maxlength="100" required>
        <label for="resume-summary">简历摘要</label>
        <textarea id="resume-summary" :value="profile.resumeSummary" @input="profile.resumeSummary = ($event.target as HTMLTextAreaElement).value" maxlength="65535" rows="6" />
      </section>
      <section class="card" aria-labelledby="projects-heading">
        <h2 id="projects-heading">项目经历（{{ profile.projects.length }}/5）</h2>
        <p v-if="!profile.projects.length" class="helper-text">尚未添加项目。</p>
        <section class="project-block" v-for="(project, index) in profile.projects" :key="project.id ?? `new-${index}`">
          <h3>项目 {{ index + 1 }}</h3>
          <label :for="`project-name-${index}`">项目名称</label>
          <input :id="`project-name-${index}`" :value="project.name" @input="project.name = ($event.target as HTMLInputElement).value" maxlength="200" required>
          <label :for="`project-description-${index}`">项目描述</label>
          <textarea :id="`project-description-${index}`" :value="project.description" @input="project.description = ($event.target as HTMLTextAreaElement).value" maxlength="65535" rows="5" />
          <div class="action-group">
            <button class="button-danger" type="button" :disabled="pending" @click="removeProject(index)">删除项目 {{ index + 1 }}</button>
          </div>
        </section>
        <div class="action-group">
          <button class="button-secondary" type="button" :disabled="pending || profile.projects.length >= 5" @click="addProject">添加项目</button>
        </div>
        <p v-if="profile.projects.length >= 5" class="helper-text">最多添加 5 个项目，请先删除项目后再添加。</p>
      </section>
      <section class="card" aria-labelledby="position-heading">
        <h2 id="position-heading">目标岗位</h2>
        <label for="position-title">目标岗位</label>
        <input id="position-title" :value="profile.positionTitle" @input="profile.positionTitle = ($event.target as HTMLInputElement).value" maxlength="200" required>
        <label for="position-description">岗位描述</label>
        <textarea id="position-description" :value="profile.positionDescription" @input="profile.positionDescription = ($event.target as HTMLTextAreaElement).value" maxlength="65535" rows="6" />
      </section>
    </fieldset>
    <p :class="dirty ? 'notice-warning' : 'notice-status'" role="status">{{ dirty ? '有未保存的修改' : '资料已同步' }}</p>
    <p v-if="saved && !dirty" class="notice-success" role="status">Profile 保存成功。</p>
    <p v-if="validationError" role="alert">{{ validationError }}</p>
    <div v-if="saveError" role="alert">
      <p>Profile 保存失败：{{ saveError.message }}</p>
      <p v-if="saveError.requestId">requestId：{{ saveError.requestId }}</p>
    </div>
    <div class="action-group">
      <button class="button-primary" type="submit" :disabled="pending || !dirty">{{ pending ? '正在保存 Profile…' : '保存 Profile' }}</button>
    </div>
  </form>
</template>

<style scoped>
.profile-fields { padding: 0; border: 0; background: transparent; }
.profile-fields > legend { padding: 0; margin-bottom: 16px; color: var(--color-muted); font-size: 0.875rem; }
.card > h2, .project-block > h3 { margin-top: 0; }
.project-block { min-width: 0; margin-block: 20px; padding: clamp(16px, 3vw, 24px); border: 1px solid var(--color-border); border-radius: var(--radius); background: #f6f8fb; }
input, textarea { display: block; width: 100%; }
.project-block > .action-group:last-child { margin-bottom: 0; }
.card > :last-child { margin-bottom: 0; }
</style>
