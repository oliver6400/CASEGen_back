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
  return <div className="border-t border-[var(--border)] pt-3">
    {processing && <p className="mb-2 text-xs font-medium text-indigo-600">CASEGen IA está respondiendo…</p>}
    <label className="mb-2 block text-xs font-medium text-slate-600">Modo IA
      <select aria-label="Modo IA" className="ml-2 rounded-md border border-[var(--border)] bg-[var(--surface)] px-2 py-1 text-xs" disabled={disabled || processing} onChange={(event) => setSkill(event.target.value as AiSkill)} value={skill}>
        <option value="GENERAL">General</option>
        <option value="UML_REVIEWER">Revisor UML</option>
        <option value="DATABASE_REVIEWER">Revisor BD</option>
      </select>
    </label>
    <textarea className="min-h-24 w-full resize-y rounded-lg border border-[var(--border)] bg-[var(--surface)] p-3 text-sm outline-none focus:border-indigo-400" disabled={disabled || processing} maxLength={MAX_LENGTH} onChange={(event) => setContent(event.target.value)} onKeyDown={onKeyDown} placeholder="Escribe una pregunta…" value={content} />
    <div className="mt-2 flex items-center justify-between gap-2">
      <span className="text-[10px] text-slate-400">{content.length}/{MAX_LENGTH} · Shift+Enter para nueva línea</span>
      <button className="primary-button" disabled={disabled || processing || !content.trim()} onClick={submit} type="button">Enviar</button>
    </div>
  </div>
}
