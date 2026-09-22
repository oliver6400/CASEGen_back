import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { getApiErrorMessage } from '../../../api/apiError'
import type { Project } from '../api/projectApi'
import { sharingApi, type SharingRole } from '../api/sharingApi'

const inviteSchema = z.object({ email: z.string().trim().email().max(254), role: z.enum(['COLLABORATOR', 'VIEWER']) })
type InviteValues = z.infer<typeof inviteSchema>

export function ProjectSharingPanel({ project, canManage }: { project: Project; canManage: boolean }) {
  const queryClient = useQueryClient()
  const members = useQuery({ queryKey: ['projects', project.id, 'members'], queryFn: () => sharingApi.members(project.id) })
  const invitations = useQuery({ queryKey: ['projects', project.id, 'invitations'], queryFn: () => sharingApi.invitations(project.id), enabled: canManage })
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['projects'] })
    await queryClient.invalidateQueries({ queryKey: ['my-invitations'] })
  }
  const action = useMutation({ mutationFn: (operation: () => Promise<unknown>) => operation(), onSuccess: refresh })
  const form = useForm<InviteValues>({ resolver: zodResolver(inviteSchema), defaultValues: { email: '', role: 'COLLABORATOR' } })
  const invite = useMutation({ mutationFn: (values: InviteValues) => sharingApi.invite(project.id, values.email, values.role),
    onSuccess: async () => { form.reset(); await refresh() } })
  const busy = action.isPending || invite.isPending
  return <section className="mb-8 grid gap-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-label="Miembros e invitaciones">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h2 className="font-semibold">Miembros e invitaciones</h2>
      {canManage ? <label className="editor-label">Visibilidad<select aria-label="Visibilidad del proyecto" className="editor-input" value={project.visibility} disabled={busy}
        onChange={(event) => { const visibility = event.target.value as Project['visibility']; action.mutate(() => sharingApi.visibility(project.id, visibility)) }}>
        <option value="PRIVATE">🔒 PRIVATE</option><option value="PUBLIC">🌐 PUBLIC</option>
      </select></label> : <span className="permission-badge">{project.visibility === 'PUBLIC' ? '🌐 PUBLIC' : '🔒 PRIVATE'}</span>}
    </div>
    <p className="text-sm text-slate-500">PUBLIC permite lectura a usuarios autenticados. Solo OWNER/COLLABORATOR pueden editar; la visibilidad nunca concede edición.</p>
    <div>
      <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-500">Miembros activos</h3>
      {members.isPending && <p className="text-sm text-slate-500">Cargando miembros…</p>}
      <div className="grid gap-2">{members.data?.map((member) => <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-100 p-3" key={member.id}>
        <div><p className="text-sm font-medium">{member.displayName}</p>{member.email && <p className="text-xs text-slate-500">{member.email}</p>}</div>
        {canManage && member.role !== 'OWNER' ? <div className="flex items-center gap-2">
          <select aria-label={`Rol de ${member.displayName}`} className="editor-input !w-40" value={member.role} disabled={busy}
            onChange={(event) => { const role = event.target.value as SharingRole; action.mutate(() => sharingApi.role(project.id, member.id, role)) }}>
            <option value="COLLABORATOR">COLLABORATOR</option><option value="VIEWER">VIEWER</option>
          </select>
          <button className="danger-button" disabled={busy} onClick={() => { if (window.confirm(`¿Revocar acceso de ${member.displayName}?`)) action.mutate(() => sharingApi.revokeMember(project.id, member.id)) }} type="button">Revocar</button>
        </div> : <span className="permission-badge">{member.role}</span>}
      </div>)}</div>
    </div>
    {canManage && <>
      <form className="grid gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end" onSubmit={form.handleSubmit((values) => invite.mutate(values))}>
        <label className="editor-label">Invitar por email<input className="editor-input" placeholder="usuario@example.com" type="email" {...form.register('email')} />{form.formState.errors.email && <span className="form-error">Email inválido.</span>}</label>
        <label className="editor-label">Rol<select className="editor-input" {...form.register('role')}><option value="COLLABORATOR">COLLABORATOR</option><option value="VIEWER">VIEWER</option></select></label>
        <button className="primary-button" disabled={busy} type="submit">Invitar</button>
      </form>
      <p className="text-xs text-slate-500">Sin envío de correo: el destinatario verá la invitación al iniciar sesión en CASEGen. Caduca en 7 días.</p>
      <div><h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-500">Invitaciones del proyecto</h3>
        {invitations.data?.length === 0 && <p className="text-sm text-slate-500">No hay invitaciones.</p>}
        {invitations.data?.map((invitation) => <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 py-2 text-sm" key={invitation.id}>
          <span>{invitation.email} · {invitation.role} · {invitation.status} · vence {new Date(invitation.expiresAt).toLocaleDateString()}</span>
          {invitation.status === 'PENDING' && <button className="danger-button" disabled={busy} onClick={() => action.mutate(() => sharingApi.revokeInvitation(project.id, invitation.id))} type="button">Revocar invitación</button>}
        </div>)}
      </div>
    </>}
    {invite.isSuccess && <p className="text-sm text-emerald-700">Invitación creada.</p>}
    {[members.error, canManage ? invitations.error : null, action.error, invite.error].filter(Boolean).map((error, index) => <p className="form-error" key={index}>{getApiErrorMessage(error)}</p>)}
  </section>
}
