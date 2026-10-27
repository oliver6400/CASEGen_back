import { useEffect, useRef } from 'react'
import type { AiMessageResponse } from '../types/aiMessage'
import { aiSkillLabel } from '../aiChatState'

export function AiMessageList({ messages, canLoadOlder, loadingOlder, onLoadOlder }: {
  messages: AiMessageResponse[]
  canLoadOlder: boolean
  loadingOlder: boolean
  onLoadOlder: () => void
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

  return <div ref={container} className="min-h-0 flex-1 space-y-3 overflow-y-auto py-3">
    {canLoadOlder && <div className="text-center"><button className="toolbar-button" disabled={loadingOlder} onClick={onLoadOlder} type="button">
      {loadingOlder ? 'Cargando…' : 'Cargar mensajes anteriores'}
    </button></div>}
    {messages.length === 0 && <p className="py-8 text-center text-sm text-slate-500">Todavía no hay mensajes en esta jornada.</p>}
    {messages.map((message) => <article key={message.id} className={`rounded-xl border p-3 ${message.role === 'ASSISTANT' ? 'border-indigo-100 bg-indigo-50/60' : 'border-[var(--border)] bg-[var(--surface)]'}`}>
      <div className="flex items-center justify-between gap-2 text-xs text-slate-500">
        <div className="flex items-center gap-1.5"><strong className="text-slate-700">{message.role === 'ASSISTANT' ? 'CASEGen IA' : message.authorName ?? 'Miembro'}</strong><span className="rounded-full bg-slate-100 px-1.5 py-0.5 text-[9px] font-medium text-slate-500">{aiSkillLabel(message.skill)}</span></div>
        <span>{formatTime(message.createdAt)}</span>
      </div>
      {message.role === 'ASSISTANT' && message.provider && <div className="mt-0.5 text-[10px] uppercase tracking-wide text-indigo-500">{message.provider}</div>}
      <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-slate-800">{message.content}</p>
      {(message.modelName || message.diagramName) && <div className="mt-2 text-[10px] text-slate-400">
        Contexto: {[message.modelName, message.diagramName].filter(Boolean).join(' / ')}
      </div>}
    </article>)}
  </div>
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
