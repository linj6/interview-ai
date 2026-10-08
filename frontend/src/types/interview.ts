// HTTP business IDs are decimal strings; scores and counts remain numbers.
// LocalDateTime strings carry no timezone.
export type InterviewMode = 'COMPREHENSIVE' | 'PROJECT_DEEP_DIVE' | 'TECHNICAL_FOCUS'
export type KnowledgeArea = 'JAVA' | 'CONCURRENCY' | 'JVM' | 'MYSQL' | 'REDIS' | 'MQ' | 'SPRING' | 'DISTRIBUTED_SYSTEMS' | 'SYSTEM_DESIGN'
export type InterviewCreateRequest = { mode: 'COMPREHENSIVE' | 'PROJECT_DEEP_DIVE'; focusArea?: never }
  | { mode: 'TECHNICAL_FOCUS'; focusArea: KnowledgeArea }
export interface InterviewPrepareResponse { interviewId: string; planId: string; initialPlanItemId: string }

export type InterviewStatus = 'CREATED' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED'
export type InterviewPhase = 'OPENING' | 'WAITING_ANSWER' | 'PROCESSING_TURN'
export type QuestionType = 'MAIN' | 'FOLLOW_UP'
export type QuestionStatus = 'CREATED' | 'WAITING_ANSWER' | 'ANSWERED' | 'SKIPPED'
export type Applicability = 'APPLICABLE' | 'NOT_APPLICABLE'
export type Importance = 'HIGH' | 'MEDIUM' | 'LOW'
export type AnswerForm = 'SAMPLE_ANSWER' | 'ANSWER_FRAMEWORK'

export interface QuestionResponse {
  id: string
  type: QuestionType
  status: QuestionStatus
  content: string
}

export interface InterviewDetailResponse {
  mode: InterviewMode
  focusArea: KnowledgeArea | null
  planPrepared: boolean
  id: string
  status: InterviewStatus
  currentPhase: InterviewPhase | null
  firstQuestion: QuestionResponse | null
  currentQuestion: QuestionResponse | null
  expiresAt: string | null
}

export interface AnswerSubmissionRequest { content: string }
export interface AnswerResponse { id: string; content: string }
export interface FeedbackResponse { description: string; relatedDimensionCodes: string[] }
export interface MissingPointResponse extends FeedbackResponse {
  importance: Importance
  explanation: string
}
export interface ReferenceAnswerResponse {
  answerForm: AnswerForm
  keyPoints: string[]
  content: string
}
export interface EvidenceResponse {
  type: 'ANSWER_QUOTE' | 'OVERALL_OBSERVATION'
  quote: string | null
  explanation: string
}
export interface DimensionScoreResponse {
  dimensionCode: string
  dimensionName: string
  applicability: Applicability
  score: number | null
  weight: number
  weightedScore: number | null
  scoreExplanation: string
  evidence: EvidenceResponse[]
}
export interface EvaluationResponse {
  id: string
  profileId: string
  overallScore10: number
  overallScore100: number
  dimensions: DimensionScoreResponse[]
  strengths: FeedbackResponse[]
  weaknesses: FeedbackResponse[]
  missingPoints: MissingPointResponse[]
  referenceAnswer: ReferenceAnswerResponse
  promptVersion: string
  evaluatorVersion: string
  model: string
}
export interface AnswerSubmissionResponse {
  answer: AnswerResponse
  evaluation: EvaluationResponse
}

export type QuestionReviewResult = 'ANSWERED' | 'SKIPPED' | 'UNANSWERED'
export interface ReviewOverview {
  startedAt: string
  completedAt: string
  durationSeconds: number
  mainQuestionCount: number
  followUpQuestionCount: number
  answeredCount: number
  skippedCount: number
  unansweredCount: number
}
export interface MissingPointReview {
  description: string
  importance: Importance
  explanation: string
}
export interface ReferenceAnswerReview {
  answerForm: AnswerForm
  keyPoints: string[]
  content: string
}
export interface DimensionScoreReview { name: string; score: number; explanation: string }
export interface EvaluationReview {
  overallScore: number
  strengths: string[]
  weaknesses: string[]
  missingPoints: MissingPointReview[]
  referenceAnswer: ReferenceAnswerReview | null
  dimensionScores: DimensionScoreReview[]
}
export interface FollowUpQuestionReview {
  questionId: string
  content: string
  result: QuestionReviewResult
  answer: string | null
  evaluation: EvaluationReview | null
}
export interface MainQuestionReview extends FollowUpQuestionReview {
  followUps: FollowUpQuestionReview[]
}
export interface InterviewReviewResponse {
  interviewId: string
  overview: ReviewOverview
  questions: MainQuestionReview[]
}
