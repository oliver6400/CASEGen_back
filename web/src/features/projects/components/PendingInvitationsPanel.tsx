import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getApiErrorMessage } from '../../../api/apiError'
import { sharingApi } from '../api/sharingApi'

export function PendingInvitationsPanel() {
  const queryClient = useQueryClient()
  const pending = useQuery({ queryKey: ['my-invitations'], queryFn: sharingApi.mine })
  const action = useMutation({ mutationFn: ({ id, accept }: { id: string; accept: boolean }) => accept ? sharingApi.accept(id) : sharingApi.reject(id),
    onSettled: async () => { await queryClient.invalidateQueries({ queryKey: ['my-invitations'] }); await queryClient.invalidateQueries({ queryKey: ['projects'] }) } })
  return <section className="mb-8 rounded-2xl border border-slate-200 bg-white p-5" aria-label="Mis invitaciones pendientes">
    <h2 className="mb-3 font-semibold">Mis invitaciones pendientes</h2>
    {pending.isPending && <p className="text-sm text-slate-500">Cargando invitaciones…</p>}
    {pending.data?.length === 0 && <p className="text-sm text-slate-500">No tienes invitaciones pendientes.</p>}
    {pending.data?.map((invitation) => <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 py-3" key={invitation.id}>
      <span className="text-sm">{invitation.projectName} · {invitation.role}</span>
      <div className="flex gap-2"><button className="primary-button" disabled={action.isPending} onClick={() => action.mutate({ id: invitation.id, accept: true })} type="button">Aceptar</button>
        <button className="danger-button" disabled={action.isPending} onClick={() => action.mutate({ id: invitation.id, accept: false })} type="button">Rechazar</button></div>
    </div>)}
    {action.isSuccess && <p className="mt-3 text-sm text-emerald-700">Invitación procesada. {action.variables.accept && <Link className="underline" to={`/projects/${action.data.projectId}`}>Abrir proyecto</Link>}</p>}
    {(pending.error || action.error) && <p className="form-error">{getApiErrorMessage(pending.error || action.error)}</p>}
  </section>
}
