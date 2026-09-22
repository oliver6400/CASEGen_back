import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { CheckCircle2, Layers } from 'lucide-react'
import { httpClient } from '../../../api/httpClient'
import { getApiErrorMessage } from '../../../api/apiError'
import { LogicalModelPanel } from './LogicalModelPanel'

interface ValidationIssue {
  id: string
  umlElementId: string | null
  severity: 'ERROR' | 'WARNING'
  code: string
  message: string
  path: string | null
}
interface ValidationRun {
  id: string
  modelId: string
  modelRevision: number
  status: 'RUNNING' | 'VALID' | 'INVALID'
  current: boolean
  issues: ValidationIssue[]
}
interface ModelVersion { id: string; versionNumber: number; checksum: string; semanticSnapshot: { model: { revision: number } } }

export function ModelValidationPanel({ modelId, revision, canEdit, onLocate }: {
  modelId: string
  revision: number
  canEdit: boolean
  onLocate: (id: string) => void
}) {
  const queryClient = useQueryClient()
  const validationKey = ['model-validation', modelId]
  const latest = useQuery({ queryKey: validationKey, queryFn: async ({ signal }) => {
    try { return (await httpClient.get<ValidationRun>(`/api/uml-models/${modelId}/validations/latest`, { signal })).data }
    catch (error) { if (axios.isAxiosError(error) && error.response?.status === 404) return null; throw error }
  } })
  const versions = useQuery({ queryKey: ['model-versions', modelId], queryFn: async () =>
    (await httpClient.get<ModelVersion[]>(`/api/uml-models/${modelId}/versions`)).data })
  const validate = useMutation({ mutationFn: async () =>
    (await httpClient.post<ValidationRun>(`/api/uml-models/${modelId}/validations`)).data,
  onMutate: () => queryClient.cancelQueries({ queryKey: validationKey }),
  onSuccess: (run) => queryClient.setQueryData(validationKey, run) })
  const createVersion = useMutation({ mutationFn: async () =>
    (await httpClient.post<ModelVersion>(`/api/uml-models/${modelId}/versions`)).data,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['model-versions', modelId] }),
  onError: () => { void queryClient.invalidateQueries({ queryKey: validationKey }); void queryClient.invalidateQueries({ queryKey: ['model-versions', modelId] }) } })
  const run = latest.data
  const current = run?.current && run.modelRevision === revision
  const capturedVersion = versions.data?.find((v) => v.semanticSnapshot.model.revision === revision)
  const versionAllowed = !capturedVersion && current && run?.status === 'VALID' && !run.issues.some((issue) => issue.severity === 'ERROR')
  const busy = validate.isPending || createVersion.isPending
  const semanticErrors = run?.issues.filter((issue) => issue.severity === 'ERROR' && !issue.path?.startsWith('/generation')) ?? []
  const umlValid = current && semanticErrors.length === 0 && run?.status !== 'RUNNING'
  const missingProfile = run?.issues.some((issue) => issue.code === 'NO_GENERATION_PROFILE')
  const missingMappings = run?.issues.some((issue) => issue.code === 'NO_ELEMENT_MAPPINGS')
  const generationErrors = run?.issues.some((issue) => issue.severity === 'ERROR' && issue.path?.startsWith('/generation'))
  return <><section className="border-b border-slate-200 p-3 text-xs" aria-label="Validación y versiones">
    <h3 className="mb-2 font-semibold uppercase text-slate-600">Validación y versiones</h3>
    {canEdit && <div className="grid gap-2">
      <button className="toolbar-button" disabled={busy} onClick={() => { createVersion.reset(); validate.mutate() }} type="button"><CheckCircle2 size={14} />{validate.isPending ? 'Validando…' : 'Validar modelo'}</button>
      <button className="toolbar-button" disabled={busy || !versionAllowed || latest.isFetching || versions.isFetching} onClick={() => createVersion.mutate()} title="Requiere revisión actual validada y aún no versionada" type="button"><Layers size={14} />Crear versión</button>
    </div>}
    <p className="my-2 text-slate-500">{run ? `${run.status} · Modelo r${run.modelRevision}${current ? '' : ' · Validación obsoleta'}` : 'Modelo todavía sin validar.'}</p>
    <div className="mb-3 grid gap-2 rounded border border-slate-200 bg-slate-50 p-2">
      <div><h4 className="font-bold text-slate-600">Estado para versionar</h4>
        {umlValid ? <p className="mt-1 text-emerald-700">✓ Modelo UML válido</p> : <p className="mt-1 text-amber-700">{current ? '⚠ Modelo UML con errores' : '⚠ Requiere validar la revisión actual'}</p>}
        {capturedVersion ? <p className="text-slate-600">La revisión r{revision} ya está versionada como v{capturedVersion.versionNumber}.</p> : versionAllowed ? <p className="text-emerald-700">✓ Listo para crear versión</p> : current && umlValid && <p className="text-amber-700">Corrige los errores del perfil antes de crear versión.</p>}
      </div>
      <div className="border-t border-slate-200 pt-2"><h4 className="font-bold text-slate-600">Estado para generar</h4>
        {!current ? <p className="mt-1 text-slate-500">Configuración pendiente de validación actual.</p> : missingProfile ? <p className="mt-1 text-amber-700">⚠ Perfil de generación no configurado</p> : missingMappings ? <p className="mt-1 text-amber-700">⚠ Mappings de generación no configurados</p> : generationErrors ? <p className="mt-1 text-amber-700">⚠ Configuración de generación con errores</p> : <p className="mt-1 text-slate-600">Perfil configurado. {versionAllowed ? 'Configuración validada.' : 'Modelo pendiente de corrección.'}</p>}
        <p className="mt-1 text-slate-500">Backend todavía no puede generarse. La generación de código aún no está implementada.</p>
      </div>
    </div>
    <div className="grid max-h-60 gap-2 overflow-y-auto">
      {run?.issues.map((issue) => <div className={`rounded border p-2 ${issue.severity === 'ERROR' ? 'border-rose-200 bg-rose-50' : 'border-amber-200 bg-amber-50'}`} key={issue.id}>
        <strong className="block break-words">{issue.severity} · {issue.code}</strong>
        <p className="mt-1 break-words">{issue.message}</p>
        {issue.umlElementId && <button className="mt-1 text-indigo-600 underline" onClick={() => onLocate(issue.umlElementId!)} type="button">Localizar elemento</button>}
      </div>)}
    </div>
    {versions.data?.length ? <p className="mt-2 text-slate-600">Versiones: {versions.data.map((version) => `v${version.versionNumber}`).join(', ')}</p> : null}
    {createVersion.isSuccess && <p className="mt-2 text-emerald-700">Versión inmutable v{createVersion.data.versionNumber} creada.</p>}
    {[latest.error, versions.error, validate.error, createVersion.error].filter(Boolean).map((error, index) => <p className="form-error mt-2" key={index}>{getApiErrorMessage(error)}</p>)}
  </section><LogicalModelPanel modelId={modelId} canEdit={canEdit} onLocate={onLocate} /></>
}
