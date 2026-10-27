export type AiMessageRole = 'USER' | 'ASSISTANT'
export type AiSkill = 'GENERAL' | 'UML_REVIEWER' | 'DATABASE_REVIEWER'

export interface AiMessageResponse {
  id: string
  sessionId: string
  role: AiMessageRole
  skill: AiSkill
  authorUserId: string | null
  authorName: string | null
  content: string
  modelId: string | null
  modelName: string | null
  diagramId: string | null
  diagramName: string | null
  provider: string | null
  replyToMessageId: string | null
  createdAt: string
}

export interface SendAiMessageRequest {
  clientMessageId: string
  content: string
  modelId?: string
  diagramId?: string
  skill: AiSkill
}

export interface SendAiMessageResponse {
  userMessage: AiMessageResponse
  assistantMessage: AiMessageResponse
  replayed: boolean
}

export interface PagedAiMessageResponse {
  content: AiMessageResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AiMessageEvent {
  eventId: string
  eventType: string
  projectId: string
  sessionId: string
  message: AiMessageResponse
  occurredAt: string
}
