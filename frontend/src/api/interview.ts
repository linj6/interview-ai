import { http } from './http'
import type { HttpOptions } from './http'
import type {
  AnswerSubmissionRequest, AnswerSubmissionResponse, InterviewDetailResponse,
  InterviewReviewResponse, InterviewCreateRequest, InterviewPrepareResponse,
} from '../types/interview'

export type InterviewRequestOptions = Pick<HttpOptions, 'signal'>

export function createInterview(request: InterviewCreateRequest, options: InterviewRequestOptions = {}): Promise<InterviewDetailResponse> {
  return http<InterviewDetailResponse>('/api/interviews', { ...options, method: 'POST', body: request })
}
export function getInterview(interviewId: string, options: InterviewRequestOptions = {}): Promise<InterviewDetailResponse> {
  return http<InterviewDetailResponse>(`/api/interviews/${interviewId}`, options)
}
export function prepareInterview(interviewId: string, options: InterviewRequestOptions = {}): Promise<InterviewPrepareResponse> {
  return http<InterviewPrepareResponse>(`/api/interviews/${interviewId}/prepare`, { ...options, method: 'POST' })
}
export function startInterview(interviewId: string, options: InterviewRequestOptions = {}): Promise<InterviewDetailResponse> {
  return http<InterviewDetailResponse>(`/api/interviews/${interviewId}/start`, { ...options, method: 'POST' })
}
export function pauseInterview(interviewId: string, options: InterviewRequestOptions = {}): Promise<InterviewDetailResponse> {
  return http<InterviewDetailResponse>(`/api/interviews/${interviewId}/pause`, { ...options, method: 'POST' })
}
export function resumeInterview(interviewId: string, options: InterviewRequestOptions = {}): Promise<InterviewDetailResponse> {
  return http<InterviewDetailResponse>(`/api/interviews/${interviewId}/resume`, { ...options, method: 'POST' })
}
export function finishInterview(interviewId: string, options: InterviewRequestOptions = {}): Promise<InterviewDetailResponse> {
  return http<InterviewDetailResponse>(`/api/interviews/${interviewId}/finish`, { ...options, method: 'POST' })
}
export function submitAnswer(interviewId: string, questionId: string, request: AnswerSubmissionRequest,
  options: InterviewRequestOptions = {}): Promise<AnswerSubmissionResponse> {
  return http<AnswerSubmissionResponse>(`/api/interviews/${interviewId}/questions/${questionId}/answer`,
    { ...options, method: 'POST', body: request })
}
export function skipQuestion(interviewId: string, questionId: string,
  options: InterviewRequestOptions = {}): Promise<InterviewDetailResponse> {
  return http<InterviewDetailResponse>(`/api/interviews/${interviewId}/questions/${questionId}/skip`,
    { ...options, method: 'POST' })
}
export function retryTurn(interviewId: string, options: InterviewRequestOptions = {}): Promise<AnswerSubmissionResponse> {
  return http<AnswerSubmissionResponse>(`/api/interviews/${interviewId}/retry`, { ...options, method: 'POST' })
}
export function getReview(interviewId: string, options: InterviewRequestOptions = {}): Promise<InterviewReviewResponse> {
  return http<InterviewReviewResponse>(`/api/interviews/${interviewId}/review`, options)
}
