export type AiSessionStatus = 'ACTIVE' | 'CLOSED'

export interface AiSessionParticipant {
  userId: string
  displayName: string
  joinedAt: string
  lastSeenAt: string
}

export interface AiSession {
  id: string
  projectId: string
  hostUserId: string
  hostName: string
  status: AiSessionStatus
  startedAt: string
  endedAt: string | null
  createdAt: string
  updatedAt: string
  participants: AiSessionParticipant[]
}

export interface AiSessionSummary {
  id: string
  projectId: string
  hostUserId: string
  hostName: string
  status: AiSessionStatus
  startedAt: string
  endedAt: string | null
  participantCount: number
}

export interface PagedAiSessionResponse {
  content: AiSessionSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
