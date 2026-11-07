import { ArrowUp, LoaderCircle } from 'lucide-react'
import { useState, type KeyboardEvent } from 'react'
import type { AiSkill } from '../types/aiMessage'

const MAX_LENGTH = 8000

export function AiMessageComposer({ disabled, processing, onSend }: {
  disabled: boolean
  processing: boolean
  onSend: (content: string, skill: AiSkill) => void
}) {
  const [content, setContent] = useState('')
  const [skill, setSkill] = useState<AiSkill>('GENERAL')
  const submit = () => {
    const trimmed = content.trim()
    if (!trimmed || disabled || processing) return
    onSend(trimmed, skill)
    setContent('')
  }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit() }
  }
  return <div className="border-t border-[var(--border)] pt-1.5">
    {processing && <p className="mb-1 text-[11px] font-medium text-indigo-600">CASEGen IA está respondiendo…</p>}
    <textarea className="min-h-14 w-full resize-none rounded-lg border border-[var(--border)] bg-[var(--surface)] px-2.5 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100" disabled={disabled || processing} maxLength={MAX_LENGTH} onChange={(event) => setContent(event.target.value)} onKeyDown={onKeyDown} placeholder="Escribe una pregunta…" value={content} />
    <div className="mt-1 flex items-end justify-between gap-2">
      <div className="min-w-0">
        <label className="flex items-center gap-1.5 text-[11px] font-medium text-slate-600">Modo IA
          <select aria-label="Modo IA" className="min-w-0 rounded-md border border-[var(--border)] bg-[var(--surface)] px-2 py-1 text-xs" disabled={disabled || processing} onChange={(event) => setSkill(event.target.value as AiSkill)} value={skill}>
            <option value="GENERAL">General</option>
            <option value="UML_REVIEWER">Revisor UML</option>
            <option value="DATABASE_REVIEWER">Revisor BD</option>
          </select>
        </label>
        <span className="mt-0.5 block text-[9px] text-slate-400">{content.length}/{MAX_LENGTH} · Shift+Enter: nueva línea</span>
      </div>
      <button aria-label={processing ? 'CASEGen IA está respondiendo' : 'Enviar mensaje'} className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-sm transition hover:-translate-y-0.5 hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:translate-y-0 disabled:cursor-not-allowed disabled:bg-indigo-300" disabled={disabled || processing || !content.trim()} onClick={submit} title={processing ? 'Procesando…' : 'Enviar'} type="button">
        {processing ? <LoaderCircle className="animate-spin" size={17} /> : <ArrowUp size={18} strokeWidth={2.5} />}
      </button>
    </div>
  </div>
}
