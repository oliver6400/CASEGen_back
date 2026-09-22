import { ChevronDown, ChevronUp, Users } from 'lucide-react'
import { useState } from 'react'
import type { CollaborationActivity, PresenceParticipant } from '../api/collaborationApi'
import { collaboratorColor, initials } from '../hooks/useRealtimeDiagram'

export function CollaborationPanel({ participants, activity }: {
  participants: PresenceParticipant[]
  activity: CollaborationActivity[]
}) {
  const [open, setOpen] = useState(false)
  return <aside className="absolute right-3 top-3 z-20 w-64 overflow-hidden rounded-xl border border-slate-200 bg-white/95 shadow-lg backdrop-blur">
    <button className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs font-bold uppercase tracking-wide text-slate-600" onClick={() => setOpen((value) => !value)} type="button">
      <Users size={15} /> Colaboradores <span className="ml-auto rounded-full bg-slate-100 px-2 py-0.5">{participants.length}</span>{open ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
    </button>
    {open && <div className="max-h-80 overflow-auto border-t border-slate-200">
      <div className="p-2">
        {participants.map((participant) => <div className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-xs" key={participant.userId}>
          <span className="grid size-7 place-items-center rounded-full text-[10px] font-bold text-white" style={{ backgroundColor: collaboratorColor(participant.userId) }}>{initials(participant.displayName)}</span>
          <span className="min-w-0 flex-1 truncate font-medium">{participant.displayName}</span>
          <span className="text-[10px] text-slate-400">{activityLabel(participant)}</span>
        </div>)}
        {participants.length === 0 && <p className="px-2 py-3 text-xs text-slate-400">Sin colaboradores conectados.</p>}
      </div>
      <div className="border-t border-slate-200 px-3 py-2 text-[10px] font-bold uppercase tracking-wider text-slate-500">Actividad</div>
      <div className="space-y-1 px-3 pb-3">
        {activity.slice(0, 20).map((entry) => <div className="flex gap-2 text-[11px] leading-4 text-slate-600" key={entry.id}>
          <time className="shrink-0 text-slate-400">{new Date(entry.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
          <span>{entry.description}</span>
        </div>)}
        {activity.length === 0 && <p className="py-2 text-xs text-slate-400">Todavía no hay cambios confirmados.</p>}
      </div>
    </div>}
  </aside>
}

function activityLabel(participant: PresenceParticipant) {
  if (participant.activity === 'EDITING') return 'Editando'
  if (participant.activity === 'SELECTING') return 'Seleccionando'
  return 'Viendo'
}
