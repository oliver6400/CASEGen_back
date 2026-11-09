import { useMutation, useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { AlertTriangle, CheckCircle2, ImageUp, RefreshCw, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { getApiErrorMessage } from '../../../api/apiError'
import {
  modelApi,
  type DetectedClassDiagram,
  type ImageImportExistingConfirmResponse,
  type ImageReconciliationAction,
  type ImageReconciliationItem,
  type ImageReconciliationPreviewResponse,
} from '../api/modelApi'
import {
  imageReconciliationDependencyIssue,
  initialImageReconciliationDecisions,
  summarizeImageReconciliation,
  buildImageImportConfirmation,
  type ImageReconciliationDecisions,
} from '../lib/imageReconciliation'
import { applyVisibilityDefaults } from '../lib/imageProposal'
import { ImageProposalEditor } from './ImageImportPanel'

const MAX_BYTES = 10 * 1024 * 1024
const ACCEPTED_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp'])

interface ExistingDiagramImageImportDialogProps {
  projectId: string
  modelId: string
  diagramId: string
  targetPackageId: string | null
  modelRevision: number
  diagramRevision: number
  onClose: () => void
  onRefreshContext: () => Promise<void>
  onConfirmed: () => Promise<void>
}

export function ExistingDiagramImageImportDialog({
  projectId, modelId, diagramId, targetPackageId, modelRevision, diagramRevision,
  onClose, onRefreshContext, onConfirmed,
}: ExistingDiagramImageImportDialogProps) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [imageUrl, setImageUrl] = useState('')
  const [proposal, setProposal] = useState<DetectedClassDiagram | null>(null)
  const [preview, setPreview] = useState<ImageReconciliationPreviewResponse | null>(null)
  const [decisions, setDecisions] = useState<ImageReconciliationDecisions>({})
  const [clientError, setClientError] = useState('')
  const [confirmationUncertain, setConfirmationUncertain] = useState(false)
  const configuration = useQuery({
    queryKey: ['image-import', 'status'],
    queryFn: modelApi.imageImportStatus,
    staleTime: 60_000,
  })

  useEffect(() => {
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setImageUrl(typeof reader.result === 'string' ? reader.result : '')
    reader.readAsDataURL(file)
    return () => reader.abort()
  }, [file])

  const analyze = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Selecciona una imagen.')
      return modelApi.analyzeImage(projectId, file)
    },
    onSuccess: (value) => {
      setProposal(applyVisibilityDefaults(value))
      setPreview(null)
      setDecisions({})
    },
  })
  const reconcile = useMutation({
    mutationFn: () => {
      if (!proposal) throw new Error('Analiza y revisa una imagen primero.')
      return modelApi.reconcileImageImport(projectId, {
        targetModelId: modelId,
        targetDiagramId: diagramId,
        targetPackageId,
        expectedModelRevision: modelRevision,
        expectedDiagramRevision: diagramRevision,
        proposal,
      })
    },
    onSuccess: (result) => {
      setPreview(result)
      setDecisions(initialImageReconciliationDecisions(result))
    },
  })
  const confirm = useMutation({
    mutationFn: (request: ReturnType<typeof buildImageImportConfirmation>) =>
      modelApi.confirmExistingImageImport(projectId, request),
    onSuccess: async () => {
      setConfirmationUncertain(false)
      await onConfirmed()
    },
    onError: (error) => {
      const status = axios.isAxiosError(error) ? error.response?.status : undefined
      if (status === 409) {
        setPreview(null)
        setDecisions({})
      }
      if (axios.isAxiosError(error) && !error.response) setConfirmationUncertain(true)
    },
  })

  const chooseFile = (candidate: File | null) => {
    setProposal(null); setPreview(null); setDecisions({}); setClientError(''); setImageUrl('')
    analyze.reset(); reconcile.reset(); confirm.reset(); setConfirmationUncertain(false)
    if (!candidate) { setFile(null); return }
    if (!ACCEPTED_TYPES.has(candidate.type)) {
      setFile(null); setClientError('Use una imagen PNG, JPG, JPEG o WEBP.'); return
    }
    if (candidate.size > MAX_BYTES) {
      setFile(null); setClientError('La imagen no puede superar 10 MB.'); return
    }
    setFile(candidate)
  }
  const changeProposal = (value: DetectedClassDiagram) => {
    setProposal(value)
    setPreview(null)
    setDecisions({})
    reconcile.reset()
    confirm.reset()
    setConfirmationUncertain(false)
  }
  const refreshAfterConflict = async () => {
    await onRefreshContext()
    setPreview(null)
    setDecisions({})
    reconcile.reset()
    confirm.reset()
    setConfirmationUncertain(false)
  }

  const confirmIncorporation = () => {
    if (!preview || !proposal || confirm.isPending || confirmationUncertain) return
    setClientError('')
    try {
      const request = buildImageImportConfirmation(preview, proposal, decisions)
      const creates = request.decisions.filter((decision) => decision.action === 'CREATE').length
      if (creates > 0 && !window.confirm(
        `Se crearán ${creates} elementos UML. ¿Confirmas la incorporación?`,
      )) return
      confirm.mutate(request)
    } catch (error) {
      setClientError(error instanceof Error ? error.message : 'Las decisiones no son válidas.')
    }
  }

  const verifyAfterUncertainFailure = async () => {
    await onRefreshContext()
    setConfirmationUncertain(false)
    setPreview(null)
    setDecisions({})
    confirm.reset()
  }

  return <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/55 p-3" role="dialog" aria-modal="true" aria-labelledby="image-import-title">
    <section className="flex max-h-[96vh] w-[min(1180px,98vw)] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl">
      <header className="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4">
        <div>
          <h2 id="image-import-title" className="text-lg font-bold text-slate-900">Imagen a UML · diagrama actual</h2>
          <p className="mt-1 text-sm text-slate-500">El análisis y la reconciliación son una vista previa. No modifican el modelo ni el diagrama.</p>
          <p className="mt-1 text-xs text-slate-400">Modelo r{modelRevision} · Diagrama r{diagramRevision}</p>
        </div>
        <button className="icon-button" type="button" disabled={confirm.isPending}
          onClick={onClose} aria-label="Cerrar importación"><X size={18} /></button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto p-5">
        {!proposal && <section className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <input ref={input} className="block flex-1 text-sm text-slate-600" type="file"
              accept=".png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp"
              onChange={(event) => chooseFile(event.target.files?.[0] ?? null)} />
            <button className="primary-button" type="button"
              disabled={!file || analyze.isPending || configuration.data?.configured === false}
              onClick={() => analyze.mutate()}>
              <ImageUp size={17} /> {analyze.isPending ? 'Analizando…' : 'Analizar imagen'}
            </button>
          </div>
          {configuration.isPending && <p className="mt-3 text-sm text-slate-500">Comprobando el servicio de análisis…</p>}
          {configuration.data?.configured === false && <p className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">El servicio de análisis de imágenes no está configurado.</p>}
          {clientError && <p className="form-error mt-3">{clientError}</p>}
          {analyze.isError && <p className="form-error mt-3">{contextualError(analyze.error)}</p>}
        </section>}

        {proposal && <ImageProposalEditor proposal={proposal} previewUrl={imageUrl}
          analysisProvider={configuration.data?.provider ?? 'GEMINI'}
          analysisModel={configuration.data?.model ?? 'gemini-2.5-flash'}
          onChange={changeProposal} onCancel={onClose} onConfirm={() => reconcile.mutate()}
          pending={reconcile.isPending} submitLabel={preview ? 'Recalcular reconciliación' : 'Calcular reconciliación'}
          pendingLabel="Comparando…" guidance="Corrija los elementos antes de calcular la reconciliación." />}

        {reconcile.isError && <ReconciliationError error={reconcile.error} onRefresh={refreshAfterConflict} />}
        {clientError && proposal && <p className="form-error mt-4">{clientError}</p>}
        {confirm.isError && <ConfirmationError error={confirm.error}
          uncertain={confirmationUncertain} onRefresh={confirmationUncertain
            ? verifyAfterUncertainFailure : refreshAfterConflict} />}
        {confirm.data && <ConfirmationResult result={confirm.data} onClose={onClose} />}
        {preview && !confirm.data && <ReconciliationResults proposal={proposal!} preview={preview}
          decisions={decisions} onChange={setDecisions} pending={confirm.isPending}
          blocked={confirmationUncertain} onConfirm={confirmIncorporation} />}
      </div>
    </section>
  </div>
}

function ReconciliationResults({ proposal, preview, decisions, onChange, pending, blocked, onConfirm }: {
  proposal: DetectedClassDiagram
  preview: ImageReconciliationPreviewResponse
  decisions: ImageReconciliationDecisions
  onChange: (value: ImageReconciliationDecisions) => void
  pending: boolean
  blocked: boolean
  onConfirm: () => void
}) {
  const summary = summarizeImageReconciliation(preview, decisions)
  return <section className="mt-6 border-t border-slate-200 pt-5" aria-label="Resultado de reconciliación">
    <div className="grid gap-2 sm:grid-cols-4">
      <Summary label="Crear" value={summary.create} tone="emerald" />
      <Summary label="Mapear" value={summary.map} tone="indigo" />
      <Summary label="Omitir" value={summary.skip} tone="slate" />
      <Summary label="Pendientes" value={summary.pending} tone="amber" />
    </div>
    {preview.items.length === 0
      ? <p className="mt-4 rounded-xl border border-slate-200 p-4 text-sm text-slate-500">La propuesta no contiene elementos para reconciliar.</p>
      : <div className="mt-4 space-y-3">{preview.items.map((item) => {
          const decision = decisions[item.temporaryId] ?? { action: 'CONFLICT' as const, candidateId: null }
          const dependencyIssue = imageReconciliationDependencyIssue(item, preview, decisions)
          const relation = item.elementKind === 'RELATIONSHIP'
            ? proposal.detectedRelationships.find((value) => value.id === item.temporaryId) : null
          const names = new Map(proposal.detectedClassifiers.map((value) => [value.id, value.name]))
          return <article key={item.temporaryId} className={`rounded-xl border p-4 ${decision.action === 'CONFLICT' || dependencyIssue ? 'border-amber-300 bg-amber-50/50' : 'border-slate-200'}`}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="flex flex-wrap items-center gap-2"><span className="permission-badge">{kindLabel(item)}</span><strong>{item.detectedName || '(sin nombre)'}</strong></div>
                {relation && <p className="mt-1 text-sm font-medium text-slate-600">{names.get(relation.source.classifierId) ?? relation.source.classifierId} → {names.get(relation.target.classifierId) ?? relation.target.classifierId}</p>}
                <p className="mt-2 text-sm text-slate-600">{item.explanation}</p>
                <p className="mt-1 text-xs text-slate-400">{item.reasonCode}</p>
              </div>
              <label className="text-sm font-medium">Decisión
                <select className="ml-2 rounded-lg border border-slate-300 bg-white px-2 py-1.5" value={decision.action}
                  onChange={(event) => changeDecision(item, event.target.value as ImageReconciliationAction, decisions, onChange)}>
                  <option value="CONFLICT">Pendiente / conflicto</option>
                  <option value="CREATE" disabled={Boolean(dependencyIssue) || item.suggestedAction !== 'CREATE'}>Crear</option>
                  {item.candidates.length > 0 && <option value="MAP_TO_EXISTING" disabled={Boolean(dependencyIssue)}>Mapear a existente</option>}
                  <option value="SKIP">Omitir</option>
                </select>
              </label>
            </div>
            {item.dependsOnTemporaryIds.length > 0 && <p className="mt-3 text-xs text-slate-500">Depende de: {item.dependsOnTemporaryIds.map((id) => names.get(id) ?? id).join(', ')}</p>}
            {dependencyIssue && <p className="mt-2 flex items-center gap-2 text-sm font-medium text-amber-800"><AlertTriangle size={15} /> {dependencyIssue}</p>}
            {item.candidates.length > 0 && <div className="mt-3 rounded-lg bg-slate-50 p-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Candidatos existentes</p>
              {decision.action === 'MAP_TO_EXISTING' && <select className="mt-2 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                value={decision.candidateId ?? ''} onChange={(event) => onChange({ ...decisions, [item.temporaryId]: { ...decision, candidateId: event.target.value || null } })}>
                <option value="">Seleccione un candidato</option>
                {item.candidates.map((candidate) => <option key={candidate.elementId} value={candidate.elementId}>{candidate.name || item.detectedName} · {candidate.classifierKind ?? item.elementKind} · {candidate.elementId}</option>)}
              </select>}
              <ul className="mt-2 space-y-1 text-xs text-slate-500">{item.candidates.map((candidate) => <li key={candidate.elementId}>{candidate.name || item.detectedName} · {candidate.elementId}{candidate.diagramNodeId ? ' · ya visible en el diagrama' : ''}</li>)}</ul>
            </div>}
          </article>
        })}</div>}
    <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-indigo-200 bg-indigo-50 p-4 text-sm font-medium text-indigo-900">
      <span>{summary.pending > 0
        ? `Quedan ${summary.pending} decisiones o referencias pendientes.`
        : `Listo: ${summary.create} crear, ${summary.map} mapear y ${summary.skip} omitir.`}</span>
      <button className="primary-button" type="button"
        disabled={summary.pending > 0 || pending || blocked || preview.items.length === 0}
        onClick={onConfirm}>{pending ? 'Incorporando…' : 'Confirmar incorporación'}</button>
    </div>
  </section>
}

function changeDecision(item: ImageReconciliationItem, action: ImageReconciliationAction,
  decisions: ImageReconciliationDecisions, onChange: (value: ImageReconciliationDecisions) => void) {
  const previous = decisions[item.temporaryId]
  const candidateId = action === 'MAP_TO_EXISTING'
    ? (item.candidates.some((value) => value.elementId === previous?.candidateId)
        ? previous.candidateId : item.candidates.length === 1 ? item.candidates[0].elementId : null)
    : null
  onChange({ ...decisions, [item.temporaryId]: { action, candidateId } })
}

function ConfirmationResult({ result, onClose }: {
  result: ImageImportExistingConfirmResponse
  onClose: () => void
}) {
  return <section className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 p-5 text-emerald-900">
    <div className="flex items-center gap-2"><CheckCircle2 size={20} /><h3 className="font-bold">Incorporación completada</h3></div>
    <div className="mt-4 grid gap-2 sm:grid-cols-4">
      <Summary label="Elementos creados" value={result.createdElementCount} tone="emerald" />
      <Summary label="Reutilizados" value={result.reusedElementCount} tone="indigo" />
      <Summary label="Nodos creados" value={result.createdNodeCount} tone="emerald" />
      <Summary label="Relaciones visibles" value={result.createdEdgeCount + result.reusedEdgeCount} tone="slate" />
    </div>
    <p className="mt-3 text-sm">Modelo r{result.modelRevision} · Diagrama r{result.diagramRevision}. Se omitieron {result.skippedElementCount} elementos.</p>
    <button className="primary-button mt-4" type="button" onClick={onClose}>Cerrar</button>
  </section>
}

function ConfirmationError({ error, uncertain, onRefresh }: {
  error: unknown
  uncertain: boolean
  onRefresh: () => Promise<void>
}) {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined
  const title = status === 403
    ? 'No tienes permiso para incorporar elementos en este proyecto.'
    : status === 409
      ? 'El modelo o diagrama cambió antes de confirmar.'
      : status === 400
        ? 'Las decisiones fueron rechazadas. Revísalas antes de volver a confirmar.'
        : uncertain
          ? 'No se pudo confirmar si el servidor completó la incorporación.'
          : 'No se pudo incorporar la propuesta.'
  return <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
    <p className="font-semibold">{title}</p>
    <p className="mt-1">{contextualError(error)}</p>
    {(status === 409 || uncertain) && <button className="toolbar-button mt-3" type="button"
      onClick={() => void onRefresh()}><RefreshCw size={15} />
      {uncertain ? 'Consultar estado antes de reintentar' : 'Actualizar y recalcular reconciliación'}
    </button>}
  </div>
}

function ReconciliationError({ error, onRefresh }: { error: unknown; onRefresh: () => Promise<void> }) {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined
  return <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
    <p className="font-semibold">{status === 403 ? 'No tienes permiso para importar en este proyecto.' : status === 409 ? 'El modelo o diagrama cambió mientras preparabas la vista previa.' : 'No se pudo calcular la reconciliación.'}</p>
    <p className="mt-1">{contextualError(error)}</p>
    {status === 409 && <button className="toolbar-button mt-3" type="button" onClick={() => void onRefresh()}><RefreshCw size={15} /> Actualizar revisiones para recalcular</button>}
  </div>
}

function Summary({ label, value, tone }: { label: string; value: number; tone: 'emerald' | 'indigo' | 'slate' | 'amber' }) {
  const colors = { emerald: 'border-emerald-200 bg-emerald-50 text-emerald-800', indigo: 'border-indigo-200 bg-indigo-50 text-indigo-800', slate: 'border-slate-200 bg-slate-50 text-slate-700', amber: 'border-amber-200 bg-amber-50 text-amber-800' }
  return <div className={`rounded-xl border p-3 ${colors[tone]}`}><span className="text-xs font-semibold uppercase">{label}</span><strong className="ml-2 text-lg">{value}</strong></div>
}

function kindLabel(item: ImageReconciliationItem) {
  return { CLASSIFIER: 'Clase / interfaz', ATTRIBUTE: 'Atributo', OPERATION: 'Operación', RELATIONSHIP: 'Relación' }[item.elementKind]
}

function contextualError(error: unknown) {
  if (axios.isAxiosError(error) && !error.response) return 'No se pudo conectar con el backend. Compruebe que Spring Boot esté activo.'
  return getApiErrorMessage(error)
}
