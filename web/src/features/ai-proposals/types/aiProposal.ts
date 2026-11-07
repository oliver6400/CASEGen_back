import type { AiSkill } from '../../ai-chat/types/aiMessage'

export type AiProposalStatus = 'PENDING' | 'APPLIED' | 'REJECTED'
export type AiProposalType = 'ADD_CLASSIFIER' | 'ADD_ATTRIBUTE' | 'ADD_OPERATION'
  | 'ADD_ASSOCIATION' | 'ADD_GENERALIZATION' | 'ADD_REALIZATION'
  | 'UPDATE_CLASSIFIER' | 'UPDATE_ATTRIBUTE'

export interface AiProposalResponse {
  id: string
  batchId: string | null
  projectId: string
  sessionId: string
  sourceMessageId: string
  requestedByUserId: string
  requestedByName: string
  status: AiProposalStatus
  proposalType: AiProposalType
  title: string
  rationale: string
  payload: Record<string, unknown>
  payloadSchemaVersion: number
  modelId: string | null
  modelName: string | null
  diagramId: string | null
  diagramName: string | null
  baseModelRevision: number | null
  baseDiagramRevision: number | null
  currentModelRevision: number | null
  currentDiagramRevision: number | null
  modelStale: boolean
  diagramContextChanged: boolean
  decidedByUserId: string | null
  decidedByName: string | null
  decidedAt: string | null
  decisionNote: string | null
  appliedModelRevision: number | null
  appliedElementId: string | null
  provider: string
  skill: AiSkill
  createdAt: string
  updatedAt: string
}

export interface CreateAiProposalResponse {
  proposal: AiProposalResponse
  replayed: boolean
}

export interface PagedAiProposalResponse {
  content: AiProposalResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AiProposalCreatedEvent {
  eventId: string
  eventType: 'AI_PROPOSAL_CREATED'
  projectId: string
  proposal: AiProposalResponse
  occurredAt: string
}

export interface AiProposalAppliedEvent extends Omit<AiProposalCreatedEvent, 'eventType'> {
  eventType: 'AI_PROPOSAL_APPLIED'
}

export interface AiProposalRejectedEvent extends Omit<AiProposalCreatedEvent, 'eventType'> {
  eventType: 'AI_PROPOSAL_REJECTED'
}
