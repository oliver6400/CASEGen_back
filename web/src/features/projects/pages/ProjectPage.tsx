import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Boxes, Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router-dom'
import { z } from 'zod'
import { getApiErrorMessage } from '../../../api/apiError'
import { AppShell } from '../../../components/layout/AppShell'
import { FormField } from '../../../components/ui/FormField'
import { PageState } from '../../../components/ui/PageState'
import { useProjectPermission } from '../../auth/hooks/useProjectPermission'
import { modelApi } from '../../modeling/api/modelApi'
import { ModelImportPanel } from '../../modeling/components/ModelImportPanel'
import { projectApi } from '../api/projectApi'
import { ProjectSharingPanel } from '../components/ProjectSharingPanel'

const schema = z.object({ name: z.string().trim().min(1, 'El nombre es obligatorio.').max(180) })
type ModelValues = z.infer<typeof schema>

export function ProjectPage() {
  const { projectId = '' } = useParams()
  const queryClient = useQueryClient()
  const permission = useProjectPermission(projectId)
  const project = useQuery({ queryKey: ['projects', projectId], queryFn: () => projectApi.get(projectId), enabled: Boolean(projectId) })
  const models = useQuery({ queryKey: ['projects', projectId, 'models'], queryFn: () => modelApi.list(projectId), enabled: Boolean(projectId) })
  const form = useForm<ModelValues>({ resolver: zodResolver(schema) })
  const createModel = useMutation({
    mutationFn: (values: ModelValues) => modelApi.create(projectId, values),
    onSuccess: async () => {
      form.reset()
      await queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'models'] })
    },
  })

  if (project.isPending) return <AppShell><PageState title="Cargando proyecto…" /></AppShell>
  if (project.isError) return <AppShell><PageState title="No se pudo abrir el proyecto" detail={getApiErrorMessage(project.error)} /></AppShell>

  return (
    <AppShell>
      <main className="mx-auto max-w-6xl px-5 py-10">
        <Link className="mb-6 inline-flex items-center gap-2 text-sm font-medium text-slate-500 hover:text-indigo-600" to="/projects"><ArrowLeft size={16} /> Proyectos</Link>
        <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
          <div><p className="eyebrow">Proyecto</p><h1 className="page-title">{project.data.name}</h1><p className="mt-2 text-slate-500">{project.data.description || 'Sin descripción'}</p></div>
          <span className="permission-badge">{permission.data?.systemRole === 'SUPERUSER' ? 'SUPERUSER' : permission.data?.projectRole ?? (permission.data ? 'PUBLIC · Solo lectura' : 'CARGANDO')}</span>
        </div>

        {permission.data?.canView && <ProjectSharingPanel project={project.data} canManage={permission.data.canManageMembers} />}
        {permission.data?.canEdit && (
          <><section className="mb-8 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-center gap-2 font-semibold"><Plus size={18} className="text-indigo-600" /> Nuevo modelo UML</div>
            <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={form.handleSubmit((values) => createModel.mutate(values))}>
              <div className="flex-1"><FormField id="model-name" label="Nombre" placeholder="Modelo de dominio" error={form.formState.errors.name?.message} {...form.register('name')} /></div>
              <button className="primary-button" disabled={createModel.isPending} type="submit">Crear modelo</button>
            </form>
            {createModel.isError && <p className="form-error mt-3">{getApiErrorMessage(createModel.error)}</p>}
          </section><ModelImportPanel projectId={projectId} /></>
        )}

        {permission.isError && <p className="form-error mb-6">{getApiErrorMessage(permission.error)}</p>}
        {models.isPending && <PageState title="Cargando modelos…" />}
        {models.isError && <PageState title="No se pudieron cargar los modelos" detail={getApiErrorMessage(models.error)} />}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {models.data?.map((model) => (
            <Link className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm hover:border-indigo-200" key={model.id} to={`/projects/${projectId}/models/${model.id}`}>
              <Boxes className="mb-6 text-indigo-600" />
              <h2 className="font-semibold">{model.name}</h2>
              <p className="mt-2 text-sm text-slate-500">UML {model.umlVersion} · revisión {model.revision}</p>
            </Link>
          ))}
        </div>
      </main>
    </AppShell>
  )
}
