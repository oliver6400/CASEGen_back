import { useState } from 'react'
import type { AiMessageResponse } from '../../ai-chat/types/aiMessage'
import { useGenerateAiProposalBatch } from '../hooks/useAiProposalBatches'

export function CreateProposalBatchButton({ projectId, message, onCreated }: {
  projectId: string
  message: AiMessageResponse
  onCreated: (id: string) => void
}) {
  const mutation = useGenerateAiProposalBatch(projectId)
  const [attempt, setAttempt] = useState<string | null>(null)
  if (message.role !== 'ASSISTANT' || message.skill !== 'UML_REVIEWER') return null

  const submit = () => {
    const id = attempt ?? crypto.randomUUID()
    if (!attempt) setAttempt(id)
    mutation.mutate({ sessionId: message.sessionId, messageId: message.id, clientBatchId: id }, {
      onSuccess: ({ batch }) => {
        setAttempt(null)
        onCreated(batch.id)
      },
    })
  }

  return <div className="mt-2">
    <button className="toolbar-button" disabled={mutation.isPending} onClick={submit} type="button">
      {mutation.isPending ? 'Generando propuestas…' : mutation.isError ? 'Reintentar' : 'Generar propuestas'}
    </button>
    {mutation.isError && <p className="mt-1 text-xs text-red-700">No se pudieron generar las propuestas.</p>}
  </div>
}
