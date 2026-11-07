import type { AiSkill } from '../../ai-chat/types/aiMessage'
import type { AiProposalStatus, AiProposalType } from './aiProposal'

export type AiProposalBatchStatus = 'PENDING' | 'IN_PROGRESS' | 'APPLIED' | 'REJECTED' | 'COMPLETED'
export interface AiActor { id: string; name: string }
export interface AiProposalBatchDependency { id: string; title: string; status: AiProposalStatus }
export interface AiProposalBatchChild {
  id: string; batchId: string; batchOrder: number; clientRef: string; proposalType: AiProposalType
  title: string; rationale: string; status: AiProposalStatus; payload: Record<string, unknown>
  payloadSchemaVersion: number; baseModelRevision: number; appliedModelRevision: number | null
  appliedElementId: string | null; requestedBy: AiActor; decidedBy: AiActor | null
  decidedAt: string | null; decisionNote: string | null; createdAt: string
  dependencies: AiProposalBatchDependency[]; blocked: boolean; blockedReason: string | null
}
export interface AiProposalBatchResponse {
  id: string; projectId: string; sessionId: string; sourceMessageId: string
  requestedBy: AiActor; modelId: string; modelName: string; diagramId: string | null
  diagramName: string | null; title: string; summary: string; baseModelRevision: number
  baseDiagramRevision: number | null; modelRevisionCursor: number; currentModelRevision: number
  provider: string; skill: AiSkill; createdAt: string; appliedBy: AiActor | null
  appliedAt: string | null; derivedStatus: AiProposalBatchStatus; children: AiProposalBatchChild[]
}
export interface PagedAiProposalBatchResponse {
  content: AiProposalBatchResponse[]; page: number; size: number; totalElements: number
  totalPages: number; first: boolean; last: boolean
}
export interface CreateAiProposalBatchResponse { batch: AiProposalBatchResponse; replayed: boolean }
export interface ApplyAiProposalBatchResponse { batchId: string; appliedCount: number; finalModelRevision: number; appliedAt: string }
export interface AiProposalBatchCreatedEvent { eventId:string; eventType:'AI_PROPOSAL_BATCH_CREATED'; projectId:string; batchId:string; occurredAt:string }
export interface AiProposalBatchAppliedEvent { eventId:string; eventType:'AI_PROPOSAL_BATCH_APPLIED'; projectId:string; batchId:string; appliedCount:number; finalModelRevision:number; occurredAt:string }
