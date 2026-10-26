import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDown, ChevronLeft, ChevronRight, History } from 'lucide-react'
import { getApiErrorMessage } from '../../../api/apiError'
import { aiSessionApi } from '../api/aiSessionApi'
import { formatSessionDate } from '../aiSessionUi'
import { sessionHistoryKey } from '../hooks/useAiSession'

export function AiSessionHistory({ projectId }: { projectId: string }) {
  const [open, setOpen] = useState(true)
  const [page, setPage] = useState(0)
  const history = useQuery({ queryKey: sessionHistoryKey(projectId, page), queryFn: () => aiSessionApi.getSessionHistory(projectId, page, 20), enabled: open })

  return <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
    <button className="flex w-full items-center justify-between gap-3 p-5 text-left" onClick={() => setOpen((value) => !value)} type="button" aria-expanded={open}>
      <span className="flex items-center gap-2 font-semibold"><History className="text-indigo-600" size={18} /> Historial de jornadas</span>
      <ChevronDown className={`transition ${open ? 'rotate-180' : ''}`} size={18} />
    </button>
    {open && <div className="border-t border-slate-100 p-5">
      {history.isPending && <p className="text-sm text-slate-500">Cargando historial…</p>}
      {history.isError && <p className="form-error">{getApiErrorMessage(history.error)}</p>}
      {history.data?.content.length === 0 && <p className="text-sm text-slate-500">No hay jornadas anteriores.</p>}
      <div className="grid gap-3">{history.data?.content.map((session) => <article className="rounded-xl border border-slate-100 p-4" key={session.id}>
        <div className="flex flex-wrap items-center justify-between gap-2"><strong className="text-sm">{formatSessionDate(session.startedAt)}</strong><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${session.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{session.status === 'ACTIVE' ? 'Activa' : 'Cerrada'}</span></div>
        <p className="mt-2 text-sm text-slate-500">{session.endedAt ? `Finalizó ${formatSessionDate(session.endedAt)}` : 'En curso'} · Host: {session.hostName}</p>
        <p className="mt-1 text-sm text-slate-500">{session.participantCount} participante{session.participantCount === 1 ? '' : 's'}</p>
      </article>)}</div>
      {history.data && history.data.totalPages > 1 && <div className="mt-4 flex items-center justify-center gap-3">
        <button className="toolbar-button" disabled={history.data.first || history.isFetching} onClick={() => setPage((value) => Math.max(0, value - 1))} type="button"><ChevronLeft size={15} /> Anterior</button>
        <span className="text-sm text-slate-500">Página {history.data.page + 1} de {history.data.totalPages}</span>
        <button className="toolbar-button" disabled={history.data.last || history.isFetching} onClick={() => setPage((value) => value + 1)} type="button">Siguiente <ChevronRight size={15} /></button>
      </div>}
    </div>}
  </section>
}
