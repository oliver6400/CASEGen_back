import type { AiProposalAppliedEvent, AiProposalCreatedEvent, AiProposalRejectedEvent } from '../ai-proposals/types/aiProposal'
import type { AiMessageEvent } from './types/aiMessage'
import type { AiProposalBatchAppliedEvent, AiProposalBatchCreatedEvent } from '../ai-proposals/types/aiProposalBatch'

export type ProjectAiEvent = AiMessageEvent | AiProposalCreatedEvent | AiProposalAppliedEvent
  | AiProposalRejectedEvent | AiProposalBatchCreatedEvent | AiProposalBatchAppliedEvent

export function parseProjectAiEvent(value: string): ProjectAiEvent | null {
  try {
    const candidate = JSON.parse(value) as { eventType?: unknown }
    return candidate.eventType === 'AI_MESSAGE_CREATED'
      || candidate.eventType === 'AI_PROPOSAL_CREATED'
      || candidate.eventType === 'AI_PROPOSAL_APPLIED'
      || candidate.eventType === 'AI_PROPOSAL_REJECTED'
      || candidate.eventType === 'AI_PROPOSAL_BATCH_CREATED'
      || candidate.eventType === 'AI_PROPOSAL_BATCH_APPLIED'
      ? candidate as ProjectAiEvent : null
  } catch {
    return null
  }
}
