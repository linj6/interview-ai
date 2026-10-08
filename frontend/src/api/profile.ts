import { ApiError, http } from './http'
import type { HttpOptions } from './http'
import type { InterviewProfile } from '../types/profile'
import { isBusinessId } from '../types/id'

type ProfileOptions = Pick<HttpOptions, 'signal'>

function canonical(value: InterviewProfile): InterviewProfile {
  const textFields = ['candidateName', 'resumeSummary', 'positionTitle', 'positionDescription'] as const
  if (!value || textFields.some(key => typeof value[key] !== 'string')
    || !Array.isArray(value.projects) || value.projects.length > 5
    || value.projects.some(p => !p || !isBusinessId(p.id) || typeof p.name !== 'string' || typeof p.description !== 'string')
    || new Set(value.projects.map(p => p.id)).size !== value.projects.length) {
    throw new ApiError('contract', null, null, '服务器未返回有效的 Profile', null)
  }
  return value
}

export async function getProfile(options: ProfileOptions = {}): Promise<InterviewProfile> {
  return canonical(await http<InterviewProfile>('/api/interview-profile', { ...options }))
}

export async function saveProfile(profile: InterviewProfile, options: ProfileOptions = {}): Promise<InterviewProfile> {
  return canonical(await http<InterviewProfile>('/api/interview-profile',
    { ...options, method: 'PUT', body: profile }))
}

export function validateProfile(profile: InterviewProfile): string | null {
  const required = (value: string, max: number) => value.trim() !== '' && value.length <= max
  const text = (value: string) => value.length <= 65535 && new TextEncoder().encode(value).length <= 65535
  if (!required(profile.candidateName, 100)) return '姓名不能为空，且不能超过 100 个字符。'
  if (!required(profile.positionTitle, 200)) return '目标岗位不能为空，且不能超过 200 个字符。'
  if (!text(profile.resumeSummary) || !text(profile.positionDescription)) return '摘要和岗位描述不能超过 65535 个字符或 UTF-8 字节。'
  if (profile.projects.length > 5) return '最多添加 5 个项目。'
  for (const project of profile.projects) {
    if (!required(project.name, 200)) return '项目名称不能为空，且不能超过 200 个字符。'
    if (!text(project.description)) return '项目描述不能超过 65535 个字符或 UTF-8 字节。'
  }
  return null
}
