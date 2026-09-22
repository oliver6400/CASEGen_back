import { useQuery } from '@tanstack/react-query'
import { FolderKanban, ShieldCheck, UsersRound } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage } from '../../../api/apiError'
import { AppShell } from '../../../components/layout/AppShell'
import { PageState } from '../../../components/ui/PageState'
import { adminApi } from '../api/adminApi'

export function AdminDashboardPage() {
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: adminApi.users })
  const projects = useQuery({ queryKey: ['admin', 'projects'], queryFn: adminApi.projects })
  const memberships = useQuery({ queryKey: ['admin', 'memberships'], queryFn: adminApi.memberships })
  const pending = users.isPending || projects.isPending || memberships.isPending
  const error = users.error ?? projects.error ?? memberships.error

  return (
    <AppShell>
      <main className="mx-auto max-w-7xl px-5 py-10">
        <p className="eyebrow">Administración</p>
        <h1 className="page-title">Resumen del sistema</h1>
        <p className="mt-2 text-slate-500">Consulta global de CASEGen. Las operaciones avanzadas quedan fuera de M1.1.</p>
        {pending && <PageState title="Cargando administración…" />}
        {error && <PageState title="No se pudo cargar el dashboard" detail={getApiErrorMessage(error)} />}
        {!pending && !error && <>
          <div className="my-8 grid gap-4 sm:grid-cols-3">
            <SummaryCard icon={<UsersRound />} label="Usuarios" value={users.data?.length ?? 0} />
            <SummaryCard icon={<FolderKanban />} label="Proyectos" value={projects.data?.length ?? 0} />
            <SummaryCard icon={<ShieldCheck />} label="Membresías" value={memberships.data?.length ?? 0} />
          </div>
          <AdminSection title="Usuarios"><table className="admin-table"><thead><tr><th>Nombre</th><th>Email</th><th>Rol</th><th>Estado</th></tr></thead><tbody>{users.data?.map((user) => <tr key={user.id}><td>{user.displayName}</td><td>{user.email}</td><td>{user.systemRole}</td><td>{user.enabled ? 'Activo' : 'Inactivo'}</td></tr>)}</tbody></table></AdminSection>
          <AdminSection title="Proyectos"><table className="admin-table"><thead><tr><th>Proyecto</th><th>Descripción</th><th>Creado</th></tr></thead><tbody>{projects.data?.map((project) => <tr key={project.id}><td><Link className="font-semibold text-indigo-600" to={`/projects/${project.id}`}>{project.name}</Link></td><td>{project.description || '—'}</td><td>{new Date(project.createdAt).toLocaleDateString()}</td></tr>)}</tbody></table></AdminSection>
          <AdminSection title="Membresías"><table className="admin-table"><thead><tr><th>Proyecto</th><th>Usuario</th><th>Rol</th><th>Estado</th></tr></thead><tbody>{memberships.data?.map((membership) => <tr key={membership.id}><td>{membership.projectName}</td><td>{membership.userEmail}</td><td>{membership.role}</td><td>{membership.status}</td></tr>)}</tbody></table></AdminSection>
        </>}
      </main>
    </AppShell>
  )
}

function SummaryCard({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
  return <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><span className="text-indigo-600">{icon}</span><strong className="mt-5 block text-3xl">{value}</strong><span className="text-sm text-slate-500">{label}</span></div>
}

function AdminSection({ title, children }: { title: string; children: ReactNode }) {
  return <section className="mb-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"><h2 className="border-b border-slate-200 px-5 py-4 font-semibold">{title}</h2><div className="overflow-x-auto">{children}</div></section>
}
