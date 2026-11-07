import { useState } from 'react'
import type { AiMessageResponse } from '../../ai-chat/types/aiMessage'
import { canCreateProposalFromMessage, friendlyProposalError, proposalAttemptId } from '../aiProposalState'
import { useCreateAiProposal } from '../hooks/useAiProposals'

export function CreateProposalButton({ projectId, message, onCreated }: {
  projectId: string
  message: AiMessageResponse
  onCreated: (proposalId: string) => void
}) {
  const create = useCreateAiProposal(projectId)
  const [clientProposalId, setClientProposalId] = useState<string | null>(null)
  if (!canCreateProposalFromMessage(message)) return null

  const submit = () => {
    const logicalAttempt = proposalAttemptId(clientProposalId)
    if (!clientProposalId) setClientProposalId(logicalAttempt)
    create.mutate({ sessionId: message.sessionId, messageId: message.id,
      clientProposalId: logicalAttempt }, {
      onSuccess: ({ proposal }) => {
        setClientProposalId(null)
        onCreated(proposal.id)
      },
    })
  }

  return <div className="mt-3 border-t border-indigo-100 pt-2">
    <button className="toolbar-button" disabled={create.isPending} onClick={submit} type="button">
      {create.isPending ? 'Creando propuesta…' : create.isError ? 'Reintentar' : 'Crear propuesta'}
    </button>
    {create.isError && <p className="mt-2 text-xs leading-5 text-red-700">{friendlyProposalError(create.error)}</p>}
  </div>
}
