import type { AiSession } from '../../ai-sessions/types/aiSession'
import { WorkspaceDrawer } from '../../modeling/components/WorkspaceDrawer'
import { friendlyAiError } from '../aiChatState'
import { useAiChat } from '../hooks/useAiChat'
import { AiContextBadge } from './AiContextBadge'
import { AiMessageComposer } from './AiMessageComposer'
import { AiMessageList } from './AiMessageList'

export function AiCollaborativeDrawer({ open, onClose, projectId, session, modelId, modelName,
  diagramId, diagramName }: {
  open: boolean
  onClose: () => void
  projectId: string
  session: AiSession | null | undefined
  modelId: string
  modelName: string
  diagramId?: string
  diagramName?: string
}) {
  const chat = useAiChat({ projectId, sessionId: session?.id, modelId, diagramId,
    enabled: open && session?.status === 'ACTIVE' })
  return <WorkspaceDrawer open={open} title="✨ IA colaborativa" onClose={onClose}>
    <div className="flex h-full min-h-[420px] flex-col">
      {!session || session.status !== 'ACTIVE' ? <div className="my-auto text-center">
        <h3 className="font-semibold text-slate-800">No hay una jornada colaborativa activa.</h3>
        <p className="mt-2 text-sm leading-6 text-slate-500">Para conversar con la IA, inicia una jornada desde el proyecto.</p>
      </div> : <>
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs"><span className="font-semibold text-emerald-700">Jornada activa</span><span className={chat.connected ? 'text-emerald-600' : 'text-amber-600'}>{chat.connected ? 'Tiempo real conectado' : 'Reconectando…'}</span></div>
          <AiContextBadge modelName={modelName} diagramName={diagramName} />
        </div>
        {chat.query.isError && <p className="mt-3 rounded bg-red-50 p-2 text-sm text-red-700">No se pudo cargar el historial.</p>}
        <AiMessageList messages={chat.messages} canLoadOlder={chat.query.hasNextPage} loadingOlder={chat.query.isFetchingNextPage} onLoadOlder={() => void chat.query.fetchNextPage()} />
        {chat.sendError && <div className="mb-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <p>{friendlyAiError(chat.sendError)}</p>
          {chat.failedRequest && <button className="toolbar-button mt-2" disabled={chat.isSending} onClick={chat.retry} type="button">Reintentar</button>}
        </div>}
        <AiMessageComposer key={session.id} disabled={false} processing={chat.isSending} onSend={chat.send} />
      </>}
    </div>
  </WorkspaceDrawer>
}
