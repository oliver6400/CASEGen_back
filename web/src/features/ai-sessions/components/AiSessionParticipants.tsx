import { Users } from 'lucide-react'
import type { AiSessionParticipant } from '../types/aiSession'

export function AiSessionParticipants({ participants }: { participants: AiSessionParticipant[] }) {
  return <div>
    <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700"><Users size={16} /> Participantes</div>
    {participants.length === 0
      ? <p className="text-sm text-slate-500">Todavía no hay participantes registrados.</p>
      : <ul className="grid gap-2 sm:grid-cols-2">{participants.map((participant) => <li className="rounded-xl bg-slate-50 px-3 py-2 text-sm text-slate-700" key={participant.userId}>{participant.displayName}</li>)}</ul>}
  </div>
}
