import { useEffect, useRef } from 'react'
import type { AiMessageResponse } from '../types/aiMessage'
import { aiSkillLabel } from '../aiChatState'
import { CreateProposalBatchButton } from '../../ai-proposals/components/CreateProposalBatchButton'

export function AiMessageList({ messages, canLoadOlder, loadingOlder, onLoadOlder, projectId,
  onProposalCreated }: {
  messages: AiMessageResponse[]
  canLoadOlder: boolean
  loadingOlder: boolean
  onLoadOlder: () => void
  projectId: string
  onProposalCreated: (proposalId: string) => void
}) {
  const container = useRef<HTMLDivElement>(null)
  const previousCount = useRef(0)
  useEffect(() => {
    const element = container.current
    if (!element) return
    const nearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 120
    if (previousCount.current === 0 || nearBottom) element.scrollTop = element.scrollHeight
    previousCount.current = messages.length
  }, [messages])

  return <div ref={container} className="min-h-0 flex-1 space-y-2 overflow-y-auto py-2 pr-1">
    {canLoadOlder && <div className="text-center"><button className="toolbar-button" disabled={loadingOlder} onClick={onLoadOlder} type="button">
      {loadingOlder ? 'Cargando…' : 'Cargar mensajes anteriores'}
    </button></div>}
    {messages.length === 0 && <p className="py-8 text-center text-sm text-slate-500">Todavía no hay mensajes en esta jornada.</p>}
    {messages.map((message) => {
      const assistant = message.role === 'ASSISTANT'
      const author = assistant ? 'CASEGen IA' : message.authorName ?? 'Miembro'
      return <article key={message.id} className={`ai-message-enter w-[88%] rounded-2xl border px-3 py-2.5 shadow-sm ${assistant ? 'mr-auto rounded-bl-md border-violet-200 bg-gradient-to-br from-violet-50 to-indigo-50' : 'ml-auto rounded-br-md border-sky-200 bg-sky-50'}`}>
      <div className="flex items-center justify-between gap-2 text-xs text-slate-500">
        <div className="flex min-w-0 items-center gap-1.5"><span aria-hidden="true" className={`inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${assistant ? 'bg-violet-600 text-white' : 'bg-sky-600 text-white'}`}>{assistant ? 'IA' : initials(author)}</span><strong className={assistant ? 'truncate text-violet-800' : 'truncate text-sky-800'}>{author}</strong><span className={`rounded-full px-1.5 py-0.5 text-[9px] font-medium ${assistant ? 'bg-violet-100 text-violet-600' : 'bg-sky-100 text-sky-600'}`}>{aiSkillLabel(message.skill)}</span></div>
        <span>{formatTime(message.createdAt)}</span>
      </div>
      {assistant && message.provider && <div className="ml-7 mt-0.5 text-[10px] uppercase tracking-wide text-violet-500">{message.provider}</div>}
      <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-slate-800">{message.content}</p>
      {(message.modelName || message.diagramName) && <div className="mt-2 text-[10px] text-slate-400">
        Contexto: {[message.modelName, message.diagramName].filter(Boolean).join(' / ')}
      </div>}
      <CreateProposalBatchButton projectId={projectId} message={message} onCreated={onProposalCreated} />
    </article>})}
  </div>
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'U'
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
