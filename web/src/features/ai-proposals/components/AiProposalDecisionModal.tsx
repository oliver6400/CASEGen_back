import { useEffect, useState, type FormEvent } from 'react'
import { proposalPayloadSummary, proposalTypeLabel } from '../aiProposalState'
import type { AiProposalResponse } from '../types/aiProposal'

export function AiProposalDecisionModal({ proposal, decision, pending, onCancel, onConfirm }: {
  proposal: AiProposalResponse
  decision: 'apply' | 'reject'
  pending: boolean
  onCancel: () => void
  onConfirm: (note?: string) => void
}) {
  const [note, setNote] = useState('')
  useEffect(() => {
    const close = (event: KeyboardEvent) => event.key === 'Escape' && !pending && onCancel()
    document.addEventListener('keydown', close)
    return () => document.removeEventListener('keydown', close)
  }, [onCancel, pending])
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (pending || note.length > 1000) return
    onConfirm(note.trim() || undefined)
  }
  const applying = decision === 'apply'
  return <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 p-4" role="presentation"
    onMouseDown={(event) => event.target === event.currentTarget && !pending && onCancel()}>
    <form aria-modal="true" className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-xl" onSubmit={submit} role="dialog"
      aria-labelledby="proposal-decision-title">
      <h2 className="text-lg font-bold text-slate-900" id="proposal-decision-title">
        {applying ? 'Aplicar propuesta' : 'Rechazar propuesta'}
      </h2>
      <div className="mt-3 rounded-xl bg-slate-50 p-3 text-sm text-slate-700">
        <p className="font-semibold">{proposalTypeLabel(proposal.proposalType)} · {proposal.title}</p>
        <p className="mt-1">{proposalPayloadSummary(proposal)}</p>
        {proposal.modelName && <p className="mt-1 text-xs text-slate-500">Modelo: {proposal.modelName}</p>}
      </div>
      {proposal.modelStale && <p className="mt-3 rounded-lg bg-amber-50 p-2 text-sm text-amber-900">
        El modelo cambió desde que se creó esta propuesta.</p>}
      <p className="mt-3 text-sm text-slate-600">{applying
        ? 'Esta acción modificará el modelo UML compartido.'
        : 'La propuesta quedará registrada como rechazada y no modificará el modelo.'}</p>
      <label className="mt-4 block text-sm font-semibold text-slate-700" htmlFor="proposal-decision-note">
        Nota opcional
      </label>
      <textarea autoFocus className="mt-1 min-h-24 w-full rounded-xl border border-slate-300 p-3 text-sm"
        id="proposal-decision-note" maxLength={1000} onChange={(event) => setNote(event.target.value)}
        placeholder="Motivo de la decisión (opcional)" value={note} />
      <p className="mt-1 text-right text-xs text-slate-500">{note.length} / 1000</p>
      <div className="mt-5 flex justify-end gap-2">
        <button className="toolbar-button" disabled={pending} onClick={onCancel} type="button">Cancelar</button>
        <button className={applying ? 'primary-button' : 'danger-button'} disabled={pending || note.length > 1000}
          type="submit">{pending ? (applying ? 'Aplicando…' : 'Rechazando…')
            : (applying ? 'Aplicar propuesta' : 'Rechazar propuesta')}</button>
      </div>
    </form>
  </div>
}
