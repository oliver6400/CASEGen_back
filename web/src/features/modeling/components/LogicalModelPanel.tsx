import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { getApiErrorMessage } from '../../../api/apiError'
import { logicalModelApi, type LogicalModel } from '../api/logicalModelApi'
import { LogicalPreparation } from './LogicalPreparation'
import { GenerationHistory } from './GenerationHistory'
import { NormalizationPanel } from './NormalizationPanel'

export function LogicalModelPanel({ modelId, canEdit, onLocate }: { modelId: string; canEdit: boolean; onLocate: (id: string) => void }) {
  const client = useQueryClient()
  const [versionId, setVersionId] = useState('')
  const [profileId, setProfileId] = useState('')
  const [open, setOpen] = useState(false)
  const [debugId, setDebugId] = useState('')
  const [result, setResult] = useState<LogicalModel | null>(null)
  const versions = useQuery({ queryKey: ['model-versions', modelId], queryFn: () => logicalModelApi.versions(modelId) })
  const profiles = useQuery({ queryKey: ['generation-profiles', modelId], queryFn: () => logicalModelApi.profiles(modelId) })
  const history = useQuery({ queryKey: ['logical-models', modelId], queryFn: () => logicalModelApi.list(modelId) })
  const activeVersion = versions.data?.find((v) => v.id === versionId) ?? versions.data?.at(-1)
  const activeProfile = profileId === 'new' ? undefined : profiles.data?.find((p) => p.id === profileId) ?? profiles.data?.[0]
  const mappings = useQuery({ queryKey: ['generation-mappings', modelId, activeProfile?.id], queryFn: () => logicalModelApi.mappings(modelId, activeProfile!.id), enabled: !!activeProfile })
  const previewKey = ['logical-preview', activeVersion?.id, activeProfile?.id]
  const preview = useQuery({ queryKey: previewKey, queryFn: () => logicalModelApi.preview(activeVersion!.id, activeProfile!.id), enabled: canEdit && !!activeVersion && !!activeProfile })
  const show = (model: LogicalModel) => { setResult(model); setOpen(true) }
  const build = useMutation({ mutationFn: () => logicalModelApi.build(activeVersion!.id, activeProfile!.id), onSuccess: async (model) => {
    show(model); await client.invalidateQueries({ queryKey: ['logical-models', modelId] })
  } })
  const load = useMutation({ mutationFn: (id: string) => logicalModelApi.get(id), onSuccess: show })
  const refresh = async (id?: string) => {
    if (id) setProfileId(id)
    await client.invalidateQueries({ queryKey: ['generation-profiles', modelId] })
    await client.invalidateQueries({ queryKey: ['generation-mappings', modelId] })
    await client.invalidateQueries({ queryKey: ['logical-preview'] })
    await client.invalidateQueries({ queryKey: ['model-validation', modelId] })
  }
  return <section className="border-b border-slate-200 p-3 text-xs" aria-label="Modelo lógico relacional">
    <h3 className="mb-2 font-semibold uppercase text-slate-600">Modelo lógico relacional</h3>
    <label className="editor-label">ModelVersion<select className="editor-input" value={activeVersion?.id ?? ''} disabled={build.isPending} onChange={(e) => setVersionId(e.target.value)}>
      {!versions.data?.length && <option value="">Crea primero una versión validada</option>}
      {versions.data?.map((v) => <option key={v.id} value={v.id}>Versión {v.versionNumber}</option>)}
    </select></label>
    {activeVersion && <p className="mt-2 text-slate-600">Versión seleccionada: v{activeVersion.versionNumber}<br />Revisión semántica capturada: r{activeVersion.semanticSnapshot.model.revision}</p>}
    <label className="editor-label mt-2">GenerationProfile<select className="editor-input" value={activeProfile?.id ?? 'new'} disabled={build.isPending} onChange={(e) => setProfileId(e.target.value)}>
      {profiles.data?.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      {canEdit && <option value="new">Crear nuevo perfil</option>}
      {!canEdit && !profiles.data?.length && <option value="new">Sin perfiles</option>}
    </select></label>
    {canEdit && activeVersion && (!activeProfile || (!mappings.isPending && !mappings.error)) && <LogicalPreparation
      key={`${activeVersion.id}:${JSON.stringify(activeProfile)}:${JSON.stringify(mappings.data ?? [])}`}
      modelId={modelId} profile={activeProfile} version={activeVersion} mappings={activeProfile ? mappings.data ?? [] : []}
      preview={preview.data} previewBusy={preview.isFetching} previewError={preview.error} building={build.isPending} onSaved={refresh} onBuild={() => build.mutate()} />}
    <p className="mt-3 text-slate-500">La preparación usa la versión inmutable elegida. No modifica UML ni diagramas. Sin generación Java/Spring Boot.</p>
    <h4 className="mb-2 mt-4 font-semibold">Modelos lógicos generados</h4>
    {history.isPending && <p>Cargando resultados…</p>}
    {history.data?.length === 0 && <p className="text-slate-500">Todavía no hay modelos lógicos.</p>}
    <div className="grid max-h-80 gap-2 overflow-auto">{history.data?.map((model, index) => <div className="rounded border border-slate-200 p-2" key={model.id}>
      <strong>Modelo lógico {history.data.length - index} · {model.status}</strong>
      <p>ModelVersion {model.versionNumber} · {model.profileName}</p>
      <p>{new Date(model.createdAt).toLocaleString()}</p>
      <p className="break-all font-mono text-slate-500" title={model.checksum}>Checksum: {model.checksum}</p>
      <button className="mt-1 text-indigo-700 underline" disabled={load.isPending} onClick={() => load.mutate(model.id)} type="button">Ver resultado</button>
      <NormalizationPanel logicalModelId={model.id} canAnalyze={canEdit} onLocate={onLocate} />
      <GenerationHistory logicalModelId={model.id} canGenerate={canEdit && model.status === 'VALID'} />
    </div>)}</div>
    <details className="mt-3"><summary className="cursor-pointer text-slate-500">Avanzado / debug: consultar por UUID</summary>
      <label className="editor-label mt-2">LogicalModel ID<input className="editor-input font-mono" value={debugId} onChange={(e) => setDebugId(e.target.value)} /></label>
      <button className="toolbar-button mt-2" disabled={load.isPending || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(debugId.trim())} onClick={() => load.mutate(debugId.trim())} type="button">Consultar resultado</button>
    </details>
    {[versions.error, profiles.error, mappings.error, history.error, build.error, load.error].filter(Boolean).map((error, i) => <p className="form-error mt-2" key={i}>{getApiErrorMessage(error)}</p>)}
    {open && result && <LogicalResult result={result} canEdit={canEdit} onClose={() => setOpen(false)} onLocate={(id) => { onLocate(id); setOpen(false) }} />}
  </section>
}

function LogicalResult({ result, canEdit, onClose, onLocate }: { result: LogicalModel; canEdit: boolean; onClose: () => void; onLocate: (id: string) => void }) {
  const totals = result.tables.reduce((r, t) => ({ columns: r.columns + t.columns.length, pk: r.pk + (t.primaryKey ? 1 : 0), fk: r.fk + t.foreignKeys.length, constraints: r.constraints + t.uniqueConstraints.length + t.checks.length, indexes: r.indexes + t.indexes.length }), { columns: 0, pk: 0, fk: 0, constraints: 0, indexes: 0 })
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" role="dialog" aria-modal="true" aria-label="Resultado del modelo lógico">
    <div className="max-h-[90vh] w-full max-w-5xl overflow-auto rounded-2xl bg-white p-6 text-sm shadow-xl">
      <div className="flex items-center justify-between"><h2 className="text-lg font-semibold">Modelo lógico relacional · {result.status}</h2><button className="toolbar-button" type="button" onClick={onClose} aria-label="Cerrar"><X size={18} /></button></div>
      <p className="my-3">{result.tables.length} tablas · {totals.columns} columnas · {totals.pk} PK · {totals.fk} FK · {totals.constraints} UNIQUE/CHECK · {totals.indexes} índices</p>
      <p className="mb-4 break-all text-xs text-slate-500">Checksum: {result.checksum}</p>
      <NormalizationPanel logicalModelId={result.id} canAnalyze={canEdit} onLocate={onLocate} />
      <GenerationHistory logicalModelId={result.id} canGenerate={canEdit && result.status === 'VALID'} />
      {result.issues.map((issue, i) => <div key={i} className={`mb-2 rounded border p-3 ${issue.severity === 'ERROR' ? 'border-rose-200 bg-rose-50' : 'border-amber-200 bg-amber-50'}`}>
        <strong>{issue.severity} · {issue.code}</strong><p>{issue.message}</p>{issue.sourceElementId && <button className="text-indigo-700 underline" type="button" onClick={() => onLocate(issue.sourceElementId!)}>Localizar UML</button>}
      </div>)}
      {result.status === 'INVALID' && <p className="my-3 text-rose-700">Corrige la configuración y vuelve a construir. No se ha persistido un esquema parcial válido.</p>}
      <div className="grid gap-4 md:grid-cols-2">{result.tables.map((table) => <section key={table.name} className="overflow-auto rounded-xl border border-slate-200 p-4">
        <h3 className="mb-2 font-bold">{table.name}</h3><table className="w-full text-left"><thead><tr><th>Columna</th><th>Tipo SQL</th><th>Null</th><th>Clave</th></tr></thead>
          <tbody>{table.columns.map((column) => <tr className="border-t border-slate-100" key={column.name}><td className="py-1 font-mono">{column.name}</td><td>{column.sqlType}</td><td>{column.nullable ? 'Sí' : 'No'}</td><td>{table.primaryKey?.columns.includes(column.name) ? 'PK ' : ''}{table.foreignKeys.some((f) => f.columns.some((p) => p.column === column.name)) ? 'FK' : ''}</td></tr>)}</tbody>
        </table>
        {table.primaryKey && <p className="mt-2">PK ({table.primaryKey.columns.join(', ')})</p>}
        {table.foreignKeys.map((fk) => <p className="mt-1 break-words" key={fk.name}>FK {fk.columns.map((p) => p.column).join(', ')} → {fk.referencedTable} ({fk.columns.map((p) => p.referencedColumn).join(', ')}) · ON DELETE {fk.onDelete}</p>)}
        {table.uniqueConstraints.map((key) => <p className="mt-1" key={key.name}>UNIQUE ({key.columns.join(', ')})</p>)}
        {table.checks.map((check) => <p className="mt-1" key={check.name}>CHECK ({check.expression})</p>)}
        {table.indexes.map((index) => <p className="mt-1 text-slate-500" key={index.name}>INDEX {index.name} ({index.columns.join(', ')})</p>)}
      </section>)}</div>
      <details className="mt-4 text-xs text-slate-500"><summary>Debug: identificador del resultado</summary>{result.id}</details>
    </div>
  </div>
}
