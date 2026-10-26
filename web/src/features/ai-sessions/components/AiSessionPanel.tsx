import { useState } from 'react'
import { CalendarClock, CircleStop, Play, Sparkles } from 'lucide-react'
import { getApiErrorMessage } from '../../../api/apiError'
import type { ProjectAccess } from '../../projects/api/projectApi'
import { canManageAiSession, formatSessionDate } from '../aiSessionUi'
import { useAiSession } from '../hooks/useAiSession'
import { AiSessionHistory } from './AiSessionHistory'
import { AiSessionParticipants } from './AiSessionParticipants'

export function AiSessionPanel({ projectId, userId, access }: { projectId: string; userId: string | undefined; access: ProjectAccess }) {
  const [confirmEnd, setConfirmEnd] = useState(false)
  const canManage = canManageAiSession(access)
  const { current, start, join, end } = useAiSession(projectId, userId, true)
  const session = current.data
  const mutationError = start.error ?? join.error ?? end.error

  const finish = () => {
    if (!session) return
    end.mutate(session.id, { onSuccess: () => setConfirmEnd(false) })
  }

  return <div className="mb-8 grid gap-5 lg:grid-cols-[minmax(0,1.25fr)_minmax(320px,.75fr)]">
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="ai-session-title">
      <div className="mb-5 flex items-start justify-between gap-4"><div><p className="eyebrow">Colaboración</p><h2 className="flex items-center gap-2 text-xl font-bold" id="ai-session-title"><Sparkles className="text-indigo-600" size={20} /> Jornada colaborativa</h2></div>{session?.status === 'ACTIVE' && <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700"><span className="size-2 rounded-full bg-emerald-500" /> Activa</span>}</div>

      {current.isPending && <p className="text-sm text-slate-500">Consultando jornada activa…</p>}
      {current.isError && <div className="form-error"><p>No se pudo consultar la jornada.</p><p className="mt-1">{getApiErrorMessage(current.error)}</p><button className="mt-2 underline" onClick={() => current.refetch()} type="button">Reintentar</button></div>}
      {!current.isPending && !current.isError && !session && <div className="rounded-xl bg-slate-50 p-4"><p className="font-semibold">No hay una jornada activa.</p><p className="mt-1 text-sm text-slate-500">Inicia una jornada para registrar el trabajo colaborativo del proyecto.</p>{canManage && <button className="primary-button mt-4" disabled={start.isPending} onClick={() => start.mutate()} type="button"><Play size={16} /> {start.isPending ? 'Iniciando…' : 'Iniciar jornada'}</button>}</div>}
      {session && <div className="grid gap-5">
        <div className="grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-2"><div><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Host</p><p className="mt-1 font-semibold">{session.hostName}</p></div><div><p className="flex items-center gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500"><CalendarClock size={14} /> Inicio</p><p className="mt-1 font-semibold">{formatSessionDate(session.startedAt)}</p></div></div>
        <AiSessionParticipants participants={session.participants} />
        {join.isPending && <p className="text-sm text-indigo-600">Registrando tu participación…</p>}
        {canManage && session.status === 'ACTIVE' && <div><button className="danger-button" disabled={end.isPending} onClick={() => setConfirmEnd(true)} type="button"><CircleStop size={15} /> Finalizar jornada</button></div>}
      </div>}
      {mutationError && <p className="form-error mt-4">{getApiErrorMessage(mutationError)}</p>}
    </section>
    <AiSessionHistory projectId={projectId} />

    {confirmEnd && <div className="fixed inset-0 z-[70] grid place-items-center bg-slate-950/50 p-4" role="dialog" aria-modal="true" aria-labelledby="end-session-title">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 text-left shadow-2xl"><h2 className="text-xl font-bold" id="end-session-title">¿Finalizar la jornada colaborativa?</h2><p className="mt-3 text-sm leading-6 text-slate-500">El historial permanecerá asociado a esta jornada. Esta acción cerrará la sesión activa para todos los participantes.</p><div className="mt-6 flex justify-end gap-3"><button className="toolbar-button" disabled={end.isPending} onClick={() => setConfirmEnd(false)} type="button">Cancelar</button><button className="danger-button" disabled={end.isPending} onClick={finish} type="button">{end.isPending ? 'Finalizando…' : 'Finalizar jornada'}</button></div></div>
    </div>}
  </div>
}
