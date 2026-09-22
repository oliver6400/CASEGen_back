import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, FolderKanban, Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { getApiErrorMessage } from '../../../api/apiError'
import { AppShell } from '../../../components/layout/AppShell'
import { FormField } from '../../../components/ui/FormField'
import { PageState } from '../../../components/ui/PageState'
import { projectApi } from '../api/projectApi'
import { PendingInvitationsPanel } from '../components/PendingInvitationsPanel'

const schema = z.object({
  name: z.string().trim().min(1, 'El nombre es obligatorio.').max(120),
  description: z.string().max(500).optional(),
})
type ProjectValues = z.infer<typeof schema>

export function ProjectsPage() {
  const queryClient = useQueryClient()
  const projects = useQuery({ queryKey: ['projects'], queryFn: projectApi.list })
  const form = useForm<ProjectValues>({ resolver: zodResolver(schema) })
  const createProject = useMutation({
    mutationFn: projectApi.create,
    onSuccess: async () => {
      form.reset()
      await queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
  })

  return (
    <AppShell>
      <main className="mx-auto max-w-6xl px-5 py-10">
        <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Workspace</p>
            <h1 className="page-title">Proyectos</h1>
            <p className="mt-2 text-slate-500">Administra los modelos semánticos de CASEGen.</p>
          </div>
        </div>

        <PendingInvitationsPanel />
        <section className="mb-8 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center gap-2 font-semibold"><Plus size={18} className="text-indigo-600" /> Nuevo proyecto</div>
          <form className="grid gap-4 md:grid-cols-[1fr_1.5fr_auto] md:items-end" onSubmit={form.handleSubmit((values) => createProject.mutate({ ...values, description: values.description || null }))}>
            <FormField id="project-name" label="Nombre" placeholder="Sistema de ventas" error={form.formState.errors.name?.message} {...form.register('name')} />
            <FormField id="project-description" label="Descripción" placeholder="Objetivo del proyecto" error={form.formState.errors.description?.message} {...form.register('description')} />
            <button className="primary-button" disabled={createProject.isPending} type="submit">Crear proyecto</button>
          </form>
          {createProject.isError && <p className="form-error mt-3">{getApiErrorMessage(createProject.error)}</p>}
        </section>

        {projects.isPending && <PageState title="Cargando proyectos…" />}
        {projects.isError && <PageState title="No se pudieron cargar los proyectos" detail={getApiErrorMessage(projects.error)} />}
        {projects.data?.length === 0 && <PageState title="Todavía no hay proyectos" detail="Crea el primero para comenzar a modelar." />}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {projects.data?.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </div>
      </main>
    </AppShell>
  )
}

function ProjectCard({ project }: { project: import('../api/projectApi').Project }) {
  const access = useQuery({ queryKey: ['projects', project.id, 'access'], queryFn: () => projectApi.access(project.id) })
  const role = access.data?.systemRole === 'SUPERUSER' ? 'SUPERUSER' : access.data?.projectRole
  return (
    <Link className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-lg" to={`/projects/${project.id}`}>
      <div className="mb-8 flex items-start justify-between">
        <span className="grid size-11 place-items-center rounded-xl bg-indigo-50 text-indigo-600"><FolderKanban size={21} /></span>
        <ArrowRight className="text-slate-300 transition group-hover:translate-x-1 group-hover:text-indigo-600" size={19} />
      </div>
      <div className="flex items-center justify-between gap-2"><h2 className="font-semibold">{project.name}</h2>{role && <span className="permission-badge">{role}</span>}</div>
      <p className="mt-2 line-clamp-2 min-h-10 text-sm text-slate-500">{project.description || 'Sin descripción'}</p>
      <p className="mt-3 text-xs font-semibold text-slate-500">{project.visibility === 'PUBLIC' ? '🌐 PUBLIC' : '🔒 PRIVATE'}{!role && access.data && ' · Solo lectura'}</p>
    </Link>
  )
}
