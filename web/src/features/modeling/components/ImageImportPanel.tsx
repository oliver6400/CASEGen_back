import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageUp, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage } from '../../../api/apiError'
import { modelApi, type DetectedClassDiagram, type DetectedType } from '../api/modelApi'

const MAX_BYTES = 10 * 1024 * 1024
const primitiveOptions = ['STRING', 'INTEGER', 'BOOLEAN', 'REAL', 'UNLIMITED_NATURAL']
const visibilityOptions = ['PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE']

export function ImageImportPanel({ projectId }: { projectId: string }) {
  const input = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState('')
  const [proposal, setProposal] = useState<DetectedClassDiagram | null>(null)
  const [clientError, setClientError] = useState('')
  const configuration = useQuery({
    queryKey: ['image-import', 'status'],
    queryFn: modelApi.imageImportStatus,
    staleTime: 60_000,
  })
  useEffect(() => {
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setPreviewUrl(typeof reader.result === 'string' ? reader.result : '')
    reader.readAsDataURL(file)
    return () => reader.abort()
  }, [file])

  const analyze = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Selecciona una imagen.')
      return modelApi.analyzeImage(projectId, file)
    },
    onSuccess: (value) => setProposal(applyVisibilityDefaults(value)),
  })
  const confirm = useMutation({
    mutationFn: () => {
      if (!proposal) throw new Error('Primero analiza una imagen.')
      return modelApi.confirmImageImport(projectId, proposal)
    },
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'models'] }),
  })

  const chooseFile = (candidate: File | null) => {
    setProposal(null); analyze.reset(); confirm.reset(); setClientError('')
    if (!candidate) { setFile(null); return }
    if (!['image/png', 'image/jpeg', 'image/webp'].includes(candidate.type)) {
      setFile(null); setClientError('Use una imagen PNG, JPG, JPEG o WEBP.'); return
    }
    if (candidate.size > MAX_BYTES) {
      setFile(null); setClientError('La imagen no puede superar 10 MB.'); return
    }
    setFile(candidate)
  }

  if (confirm.data) return (
    <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-slate-700">
      <h3 className="font-semibold text-emerald-800">Modelo creado</h3>
      <p className="mt-2"><strong>Modelo:</strong> {confirm.data.modelName}</p>
      <p><strong>Validación:</strong> {confirm.data.validation.status} · {confirm.data.validation.issueCount} issues</p>
      <p>{confirm.data.classCount} clases · {confirm.data.interfaceCount} interfaces · {confirm.data.attributeCount} atributos · {confirm.data.relationshipCount} relaciones</p>
      {confirm.data.warnings.length > 0 && <p className="mt-2 text-amber-700">Se conservaron {confirm.data.warnings.length} advertencias del reconocimiento.</p>}
      {confirm.data.diagram && <Link className="primary-button mt-3 inline-flex" to={`/projects/${projectId}/models/${confirm.data.modelId}/diagrams/${confirm.data.diagram.diagramId}`}>Abrir diagrama</Link>}
    </section>
  )

  return (
    <div>
      {!proposal && <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input ref={input} className="block flex-1 text-sm text-slate-600" type="file" accept=".png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp"
          onChange={(event) => chooseFile(event.target.files?.[0] ?? null)} />
        <button className="primary-button" type="button" disabled={!file || analyze.isPending || configuration.data?.configured === false} onClick={() => analyze.mutate()}>
          <ImageUp size={17} /> {analyze.isPending ? 'Analizando…' : 'Analizar imagen'}
        </button>
      </div>}
      {configuration.data?.configured === false && <p className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">El servicio de análisis de imágenes no está configurado.</p>}
      {configuration.data?.provider === 'LOCAL' && configuration.data.localOcrAvailable === false && <p className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">OCR local no disponible. Se detectará la geometría y podrá corregir los nombres manualmente.</p>}
      {clientError && <p className="form-error mt-3">{clientError}</p>}
      {analyze.isError && <p className="form-error mt-3">{getApiErrorMessage(analyze.error)}</p>}
      {configuration.data && <p className="mt-3 text-xs text-slate-500">Análisis de imagen: {providerLabel(configuration.data.provider)}</p>}
      {proposal && <ProposalEditor proposal={proposal} previewUrl={previewUrl}
        analysisProvider={configuration.data?.provider ?? 'GEMINI'} analysisModel={configuration.data?.model ?? 'gemini-2.5-flash'}
        onChange={setProposal} onCancel={() => { setProposal(null); setFile(null); if (input.current) input.current.value = '' }} onConfirm={() => confirm.mutate()} pending={confirm.isPending} />}
      {confirm.isError && <p className="form-error mt-3">{getApiErrorMessage(confirm.error)}</p>}
    </div>
  )
}

function ProposalEditor({ proposal, previewUrl, analysisProvider, analysisModel, onChange, onCancel, onConfirm, pending }: {
  proposal: DetectedClassDiagram
  previewUrl: string
  analysisProvider: string
  analysisModel: string
  onChange: (value: DetectedClassDiagram) => void
  onCancel: () => void
  onConfirm: () => void
  pending: boolean
}) {
  const classifiers = proposal.detectedClassifiers
  const usedLocalFallback = proposal.warnings.some((warning) => warning.code === 'GEMINI_LOCAL_FALLBACK')
  const [selectedClassifierId, setSelectedClassifierId] = useState(classifiers[0]?.id ?? '')
  const typeOptions = [...primitiveOptions.map((value) => ({ key: `PRIMITIVE:${value}`, label: prettyType(value) })), ...classifiers.map((value) => ({ key: `CLASSIFIER:${value.id}`, label: value.name }))]
  const update = <K extends keyof DetectedClassDiagram>(key: K, value: DetectedClassDiagram[K]) => onChange({ ...proposal, [key]: value })
  const deleteClassifier = (id: string) => {
    const remaining = classifiers.filter((value) => value.id !== id)
    if (selectedClassifierId === id) setSelectedClassifierId(remaining[0]?.id ?? '')
    onChange({
      ...proposal,
      detectedClassifiers: remaining,
      detectedAttributes: proposal.detectedAttributes.filter((value) => value.ownerClassifierId !== id && value.type.value !== id),
      detectedOperations: proposal.detectedOperations.filter((value) => value.ownerClassifierId !== id),
      detectedRelationships: proposal.detectedRelationships.filter((value) => value.source.classifierId !== id && value.target.classifierId !== id),
    })
  }

  return <div className="mt-5 space-y-5 border-t border-slate-200 pt-5">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h3 className="font-semibold">Elementos detectados</h3>
        <p className="text-sm text-slate-500">Método de análisis: {usedLocalFallback ? 'Local (fallback de Gemini)' : analysisProvider === 'GEMINI' ? prettyGeminiModel(analysisModel) : providerLabel(analysisProvider)}</p>
        <p className="text-sm text-slate-500">Confianza global: {Math.round(proposal.confidence * 100)}%. Revise antes de crear.</p></div>
      <label className="text-sm font-medium">Nombre del modelo <input className="ml-2 rounded-lg border border-slate-300 px-3 py-2" value={proposal.suggestedModelName} onChange={(event) => update('suggestedModelName', event.target.value)} /></label>
    </div>
    {proposal.warnings.length > 0 && <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"><strong>Warnings</strong><ul className="mt-1 list-disc pl-5">{proposal.warnings.map((warning, index) => <li key={`${warning.code}-${index}`}>{warning.message}</li>)}</ul></div>}

    {previewUrl && <section><h4 className="mb-2 font-semibold">Regiones detectadas</h4>
      <div className="relative inline-block max-w-full overflow-hidden rounded-xl border border-slate-300 bg-slate-100">
        <img className="block max-h-[560px] max-w-full object-contain" src={previewUrl} alt="Imagen original con classifiers detectados" />
        {classifiers.map((classifier, index) => classifier.region && <button
          key={classifier.id}
          type="button"
          title={`${index + 1}. ${classifier.name}`}
          aria-label={`Seleccionar región ${classifier.name}`}
          onClick={() => setSelectedClassifierId(classifier.id)}
          className={`absolute border-2 bg-transparent ${selectedClassifierId === classifier.id ? 'border-fuchsia-600 ring-2 ring-fuchsia-300' : 'border-indigo-500'}`}
          style={{ left: `${classifier.region.x * 100}%`, top: `${classifier.region.y * 100}%`, width: `${classifier.region.width * 100}%`, height: `${classifier.region.height * 100}%` }}>
          <span className={`absolute -top-6 left-0 whitespace-nowrap rounded px-1.5 py-0.5 text-xs text-white ${selectedClassifierId === classifier.id ? 'bg-fuchsia-700' : 'bg-indigo-600'}`}>{index + 1}. {classifier.name}</span>
        </button>)}
      </div>
    </section>}

    <section><h4 className="mb-2 font-semibold">Clases / Interfaces ({classifiers.length})</h4><div className="grid gap-2 sm:grid-cols-2">
      {classifiers.map((classifier, index) => <div onClick={() => setSelectedClassifierId(classifier.id)} className={`flex flex-wrap gap-2 rounded-xl border p-3 ${selectedClassifierId === classifier.id ? 'border-fuchsia-400 bg-fuchsia-50' : 'border-slate-200'}`} key={classifier.id}>
        <input className="min-w-0 flex-1 rounded border px-2" value={classifier.name} onChange={(event) => update('detectedClassifiers', classifiers.map((value, i) => i === index ? { ...value, name: event.target.value } : value))} />
        <select className="rounded border px-2" value={classifier.kind} onChange={(event) => update('detectedClassifiers', classifiers.map((value, i) => i === index ? { ...value, kind: event.target.value as typeof value.kind } : value))}><option>CLASS</option><option>INTERFACE</option><option>UNKNOWN</option></select>
        <select className="rounded border px-2" aria-label="Visibilidad" value={classifier.visibility} onChange={(event) => update('detectedClassifiers', classifiers.map((value, i) => i === index ? { ...value, visibility: event.target.value } : value))}>{visibilityOptions.map((value) => <option key={value}>{value}</option>)}</select>
        <button aria-label="Eliminar classifier" onClick={() => deleteClassifier(classifier.id)}><Trash2 size={16} /></button>
      </div>)}
    </div></section>

    <section><h4 className="mb-2 font-semibold">Atributos ({proposal.detectedAttributes.length})</h4><div className="space-y-2">
      {proposal.detectedAttributes.map((attribute, index) => <div className="grid gap-2 rounded-xl border border-slate-200 p-3 sm:grid-cols-[1fr_1fr_1fr_1fr_1fr_auto]" key={attribute.id}>
        <select className="rounded border px-2 py-2" value={attribute.ownerClassifierId} onChange={(event) => update('detectedAttributes', proposal.detectedAttributes.map((value, i) => i === index ? { ...value, ownerClassifierId: event.target.value } : value))}>{classifiers.map((value) => <option key={value.id} value={value.id}>{value.name}</option>)}</select>
        <input className="rounded border px-2" value={attribute.name} onChange={(event) => update('detectedAttributes', proposal.detectedAttributes.map((value, i) => i === index ? { ...value, name: event.target.value } : value))} />
        <TypeSelect value={attribute.type} options={typeOptions} onChange={(type) => update('detectedAttributes', proposal.detectedAttributes.map((value, i) => i === index ? { ...value, type } : value))} />
        <select className="rounded border px-2" value={attribute.visibility} onChange={(event) => update('detectedAttributes', proposal.detectedAttributes.map((value, i) => i === index ? { ...value, visibility: event.target.value } : value))}>{visibilityOptions.map((value) => <option key={value}>{value}</option>)}</select>
        <MultiplicityInput value={attribute} onChange={(multiplicity) => update('detectedAttributes', proposal.detectedAttributes.map((value, i) => i === index ? { ...value, ...multiplicity } : value))} />
        <button aria-label="Eliminar atributo" onClick={() => update('detectedAttributes', proposal.detectedAttributes.filter((_, i) => i !== index))}><Trash2 size={16} /></button>
      </div>)}
    </div></section>

    <section><h4 className="mb-2 font-semibold">Operaciones ({proposal.detectedOperations.length})</h4><div className="space-y-2">
      {proposal.detectedOperations.map((operation, index) => <div className="grid gap-2 rounded-xl border border-slate-200 p-3 sm:grid-cols-[1fr_1fr_1fr_1fr_auto]" key={operation.id}>
        <select className="rounded border px-2 py-2" value={operation.ownerClassifierId} onChange={(event) => update('detectedOperations', proposal.detectedOperations.map((value, i) => i === index ? { ...value, ownerClassifierId: event.target.value } : value))}>{classifiers.map((value) => <option key={value.id} value={value.id}>{value.name}</option>)}</select>
        <input className="rounded border px-2" value={operation.name} onChange={(event) => update('detectedOperations', proposal.detectedOperations.map((value, i) => i === index ? { ...value, name: event.target.value } : value))} />
        <TypeSelect allowVoid value={operation.returnType} options={typeOptions} onChange={(returnType) => update('detectedOperations', proposal.detectedOperations.map((value, i) => i === index ? { ...value, returnType } : value))} />
        <select className="rounded border px-2" value={operation.visibility} onChange={(event) => update('detectedOperations', proposal.detectedOperations.map((value, i) => i === index ? { ...value, visibility: event.target.value } : value))}>{visibilityOptions.map((value) => <option key={value}>{value}</option>)}</select>
        <button aria-label="Eliminar operación" onClick={() => update('detectedOperations', proposal.detectedOperations.filter((_, i) => i !== index))}><Trash2 size={16} /></button>
        {operation.parameters.length > 0 && <div className="space-y-1 sm:col-span-5">{operation.parameters.map((parameter, parameterIndex) => <div className="grid grid-cols-[1fr_1fr_1fr_1fr_auto] gap-2 text-xs" key={parameter.id}>
          <input className="rounded border px-2 py-1" value={parameter.name} onChange={(event) => updateParameter(proposal, update, index, parameterIndex, { name: event.target.value })} />
          <TypeSelect value={parameter.type} options={typeOptions} onChange={(type) => updateParameter(proposal, update, index, parameterIndex, { type })} />
          <select className="rounded border px-2" value={parameter.direction} onChange={(event) => updateParameter(proposal, update, index, parameterIndex, { direction: event.target.value })}><option>IN</option><option>OUT</option><option>INOUT</option><option>UNKNOWN</option></select>
          <MultiplicityInput value={parameter} onChange={(multiplicity) => updateParameter(proposal, update, index, parameterIndex, multiplicity)} />
          <button aria-label="Eliminar parámetro" onClick={() => update('detectedOperations', proposal.detectedOperations.map((value, i) => i === index ? { ...value, parameters: value.parameters.filter((_, p) => p !== parameterIndex) } : value))}><Trash2 size={14} /></button>
        </div>)}</div>}
      </div>)}
    </div></section>

    <section><h4 className="mb-2 font-semibold">Relaciones ({proposal.detectedRelationships.length})</h4><div className="space-y-3">
      {proposal.detectedRelationships.map((relationship, index) => <div className="rounded-xl border border-slate-200 p-3" key={relationship.id}>
        <div className="grid gap-2 sm:grid-cols-[1fr_1fr_1fr_auto]">
          <select className="rounded border px-2 py-2" value={relationship.source.classifierId} onChange={(event) => updateRelationship(proposal, update, index, { source: { ...relationship.source, classifierId: event.target.value } })}>{classifiers.map((value) => <option key={value.id} value={value.id}>{value.name}</option>)}</select>
          <select className="rounded border px-2 py-2" value={relationship.type} onChange={(event) => updateRelationship(proposal, update, index, { type: event.target.value as typeof relationship.type })}><option>ASSOCIATION</option><option>AGGREGATION</option><option>COMPOSITION</option><option>GENERALIZATION</option><option>REALIZATION</option><option>UNKNOWN</option></select>
          <select className="rounded border px-2 py-2" value={relationship.target.classifierId} onChange={(event) => updateRelationship(proposal, update, index, { target: { ...relationship.target, classifierId: event.target.value } })}>{classifiers.map((value) => <option key={value.id} value={value.id}>{value.name}</option>)}</select>
          <button aria-label="Eliminar relación" onClick={() => update('detectedRelationships', proposal.detectedRelationships.filter((_, i) => i !== index))}><Trash2 size={16} /></button>
        </div>
        {['ASSOCIATION', 'AGGREGATION', 'COMPOSITION'].includes(relationship.type) && <div className="mt-2 grid gap-2 text-sm sm:grid-cols-2">
          <EndEditor label="Extremo A" end={relationship.source} onChange={(source) => updateRelationship(proposal, update, index, { source })} />
          <EndEditor label="Extremo B" end={relationship.target} onChange={(target) => updateRelationship(proposal, update, index, { target })} />
        </div>}
      </div>)}
    </div></section>

    <div className="flex justify-end gap-3"><button className="secondary-button" type="button" onClick={onCancel}>Cancelar</button><button className="primary-button" type="button" disabled={pending} onClick={onConfirm}>{pending ? 'Creando…' : 'Crear modelo'}</button></div>
  </div>
}

function TypeSelect({ value, options, onChange, allowVoid = false }: { value: DetectedType; options: Array<{ key: string; label: string }>; onChange: (value: DetectedType) => void; allowVoid?: boolean }) {
  const selected = `${value.kind}:${value.value}`
  return <select className="rounded border px-2 py-2" value={selected} onChange={(event) => { const [kind, ...rest] = event.target.value.split(':'); onChange({ kind: kind as DetectedType['kind'], value: rest.join(':') }) }}>
    <option value="UNKNOWN:UNKNOWN">Tipo desconocido</option>{allowVoid && <option value="VOID:void">Sin retorno / void</option>}{options.map((option) => <option key={option.key} value={option.key}>{option.label}</option>)}
  </select>
}

function EndEditor({ label, end, onChange }: { label: string; end: DetectedClassDiagram['detectedRelationships'][number]['source']; onChange: (value: typeof end) => void }) {
  return <div className="rounded-lg bg-slate-50 p-2"><strong>{label}</strong><div className="mt-2 grid grid-cols-2 gap-2">
    <input className="rounded border px-2 py-1" placeholder="roleName" value={end.roleName} onChange={(event) => onChange({ ...end, roleName: event.target.value })} />
    <select className="rounded border px-2" value={end.visibility} onChange={(event) => onChange({ ...end, visibility: event.target.value })}>{visibilityOptions.map((value) => <option key={value}>{value}</option>)}</select>
    <MultiplicityInput key={`${end.multiplicityKnown}:${end.lowerBound}:${end.upperBound}:${end.upperUnlimited}`} value={end} onChange={(multiplicity) => onChange({ ...end, ...multiplicity })} />
    <label className="flex items-center gap-2"><input type="checkbox" checked={end.navigable} onChange={(event) => onChange({ ...end, navigable: event.target.checked })} /> Navegable</label>
  </div></div>
}

function MultiplicityInput({ value, onChange }: { value: { lowerBound: number | null; upperBound: number | null; upperUnlimited: boolean; multiplicityKnown: boolean }; onChange: (value: ReturnType<typeof parseMultiplicity>) => void }) {
  const multiplicity = !value.multiplicityKnown ? '?' : value.lowerBound === value.upperBound && !value.upperUnlimited ? `${value.lowerBound}` : `${value.lowerBound ?? '?'}..${value.upperUnlimited ? '*' : value.upperBound ?? '?'}`
  const [draft, setDraft] = useState(multiplicity)
  const commit = () => onChange(parseMultiplicity(draft))
  return <input className="rounded border px-2 py-1" aria-label="Multiplicidad" value={draft}
    onChange={(event) => setDraft(event.target.value)} onBlur={commit}
    onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); commit() } }} />
}

function updateRelationship(proposal: DetectedClassDiagram, update: <K extends keyof DetectedClassDiagram>(key: K, value: DetectedClassDiagram[K]) => void, index: number, patch: Partial<DetectedClassDiagram['detectedRelationships'][number]>) {
  update('detectedRelationships', proposal.detectedRelationships.map((value, i) => i === index ? { ...value, ...patch } : value))
}

function updateParameter(proposal: DetectedClassDiagram, update: <K extends keyof DetectedClassDiagram>(key: K, value: DetectedClassDiagram[K]) => void, operationIndex: number, parameterIndex: number, patch: Partial<DetectedClassDiagram['detectedOperations'][number]['parameters'][number]>) {
  update('detectedOperations', proposal.detectedOperations.map((operation, index) => index === operationIndex ? { ...operation, parameters: operation.parameters.map((parameter, p) => p === parameterIndex ? { ...parameter, ...patch } : parameter) } : operation))
}

function parseMultiplicity(raw: string) {
  const value = raw.trim()
  const match = value.match(/^(\d+)(?:\.\.(\d+|\*))?$/)
  if (!match) return { multiplicityKnown: false, lowerBound: null, upperBound: null, upperUnlimited: false }
  const lowerBound = Number(match[1]); const upper = match[2] ?? match[1]
  return { multiplicityKnown: true, lowerBound, upperBound: upper === '*' ? null : Number(upper), upperUnlimited: upper === '*' }
}
function prettyType(value: string) { return value.split('_').map((part) => part[0] + part.slice(1).toLowerCase()).join('') }
function providerLabel(provider: string) { return provider === 'GEMINI' ? 'Gemini' : provider === 'LOCAL' ? 'Local' : provider }
function prettyGeminiModel(model: string) {
  if (model.toLowerCase() === 'gemini-2.5-flash') return 'Gemini 2.5 Flash'
  return `Gemini (${model})`
}

function applyVisibilityDefaults(proposal: DetectedClassDiagram): DetectedClassDiagram {
  const visibility = (value: string | null | undefined, fallback: string) => {
    const normalized = value?.trim().toUpperCase()
    return !normalized || normalized === 'UNKNOWN' || normalized === 'NOT_APPLICABLE'
      ? fallback
      : normalized
  }
  const associationTypes = new Set(['ASSOCIATION', 'AGGREGATION', 'COMPOSITION'])
  return {
    ...proposal,
    detectedClassifiers: proposal.detectedClassifiers.map((value) => ({
      ...value,
      visibility: visibility(value.visibility, 'PUBLIC'),
    })),
    detectedAttributes: proposal.detectedAttributes.map((value) => ({
      ...value,
      visibility: visibility(value.visibility, 'PRIVATE'),
    })),
    detectedOperations: proposal.detectedOperations.map((value) => ({
      ...value,
      visibility: visibility(value.visibility, 'PUBLIC'),
    })),
    detectedRelationships: proposal.detectedRelationships.map((value) => associationTypes.has(value.type)
      ? {
          ...value,
          source: { ...value.source, visibility: visibility(value.source.visibility, 'PUBLIC') },
          target: { ...value.target, visibility: visibility(value.target.visibility, 'PUBLIC') },
        }
      : value),
  }
}
