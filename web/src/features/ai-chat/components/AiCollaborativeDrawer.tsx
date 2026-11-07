import { useState } from 'react'
import type { AiSession } from '../../ai-sessions/types/aiSession'
import { AiProposalBoard } from '../../ai-proposals/components/AiProposalBoard'
import { WorkspaceDrawer } from '../../modeling/components/WorkspaceDrawer'
import { friendlyAiError } from '../aiChatState'
import { useAiChat } from '../hooks/useAiChat'
import { useProjectAiRealtime } from '../hooks/useProjectAiRealtime'
import { AiContextBadge } from './AiContextBadge'
import { AiMessageComposer } from './AiMessageComposer'
import { AiMessageList } from './AiMessageList'

export function AiCollaborativeDrawer({ open, onClose, projectId, session, modelId, modelName,
  diagramId, diagramName, canEdit }: {
  open: boolean
  onClose: () => void
  projectId: string
  session: AiSession | null | undefined
  modelId: string
  modelName: string
  diagramId?: string
  diagramName?: string
  canEdit: boolean
}) {
  const [tab, setTab] = useState<'chat' | 'board'>('chat')
  const [highlightedProposalId, setHighlightedProposalId] = useState<string>()
  const chat = useAiChat({ projectId, sessionId: session?.id, modelId, diagramId,
    enabled: open && session?.status === 'ACTIVE' })
  const realtime = useProjectAiRealtime(projectId, session?.id, open)
  const showCreatedProposal = (proposalId: string) => {
    setHighlightedProposalId(proposalId)
    setTab('board')
  }

  return <WorkspaceDrawer open={open} title="✨ IA colaborativa" onClose={onClose} compactHeader>
    <div className="flex h-full min-h-[420px] flex-col">
      <div className="mb-1.5 mr-9 grid grid-cols-2 rounded-lg bg-slate-100 p-0.5" role="tablist" aria-label="Secciones de IA colaborativa">
        <button className={`rounded-md px-3 py-1 text-sm font-semibold ${tab === 'chat' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}
          onClick={() => setTab('chat')} role="tab" aria-selected={tab === 'chat'} type="button">Chat</button>
        <button className={`rounded-md px-3 py-1 text-sm font-semibold ${tab === 'board' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}
          onClick={() => setTab('board')} role="tab" aria-selected={tab === 'board'} type="button">Pizarra IA</button>
      </div>
      {tab === 'board' ? <AiProposalBoard projectId={projectId} enabled={open}
        highlightedId={highlightedProposalId} canEdit={canEdit} /> : !session || session.status !== 'ACTIVE' ? <div className="my-auto text-center">
        <h3 className="font-semibold text-slate-800">No hay una jornada colaborativa activa.</h3>
        <p className="mt-2 text-sm leading-6 text-slate-500">La pizarra IA sigue disponible para consultar propuestas anteriores.</p>
      </div> : <>
        <div className="space-y-1">
          <div className="flex items-center justify-between text-xs"><span className="font-semibold text-emerald-700">Jornada activa</span><span className={realtime.connected ? 'text-emerald-600' : 'text-amber-600'}>{realtime.connected ? 'Tiempo real conectado' : 'Reconectando…'}</span></div>
          <AiContextBadge modelName={modelName} diagramName={diagramName} />
        </div>
        {chat.query.isError && <p className="mt-3 rounded bg-red-50 p-2 text-sm text-red-700">No se pudo cargar el historial.</p>}
        <AiMessageList messages={chat.messages} canLoadOlder={chat.query.hasNextPage}
          loadingOlder={chat.query.isFetchingNextPage} onLoadOlder={() => void chat.query.fetchNextPage()}
          projectId={projectId} onProposalCreated={showCreatedProposal} />
        {chat.sendError && <div className="mb-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <p>{friendlyAiError(chat.sendError)}</p>
          {chat.failedRequest && <button className="toolbar-button mt-2" disabled={chat.isSending} onClick={chat.retry} type="button">Reintentar</button>}
        </div>}
        <AiMessageComposer key={session.id} disabled={false} processing={chat.isSending} onSend={chat.send} />
      </>}
    </div>
  </WorkspaceDrawer>
}
