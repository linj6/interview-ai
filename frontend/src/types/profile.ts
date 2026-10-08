// HTTP business IDs are decimal strings; null identifies a new project.
export interface ProfileProject {
  id: string | null
  name: string
  description: string
}

export interface InterviewProfile {
  candidateName: string
  resumeSummary: string
  projects: ProfileProject[]
  positionTitle: string
  positionDescription: string
}
