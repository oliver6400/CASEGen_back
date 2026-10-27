import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { getApiErrorMessage } from '../../../api/apiError'
import { logicalModelApi, type GenerationRun } from '../api/logicalModelApi'

const stages = ['GENERATING', 'COMPILING', 'TESTING', 'VERIFYING_DATABASE', 'PACKAGING'] as const
const terminal = (status?: string) => status === 'SUCCESS' || status === 'FAILED'
const formatSize = (bytes: number) => bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`

export function GenerationHistory({ logicalModelId, canGenerate }: { logicalModelId: string; canGenerate: boolean }) {
  const client = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const history = useQuery({
    queryKey: ['backend-generations', logicalModelId], queryFn: () => logicalModelApi.generations(logicalModelId),
    refetchInterval: (query) => query.state.data?.some((run) => !terminal(run.status)) ? 1000 : false,
  })
  const selected = useQuery({
    queryKey: ['backend-generation', selectedId], queryFn: () => logicalModelApi.generation(selectedId!), enabled: !!selectedId,
    refetchInterval: (query) => terminal(query.state.data?.status) ? false : 750,
  })
  const generate = useMutation({ mutationFn: () => logicalModelApi.generate(logicalModelId), onSuccess: async (start) => {
    setSelectedId(start.generationId); await client.invalidateQueries({ queryKey: ['backend-generations', logicalModelId] })
  } })
  const runs = history.data ?? []
  return <section className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
    <div className="rounded-lg border border-indigo-200 bg-white p-3"><h5 className="font-semibold uppercase text-indigo-700">Generadores de código</h5>{canGenerate ? <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2"><button className="toolbar-button justify-center" disabled={generate.isPending} onClick={() => generate.mutate()} type="button">{generate.isPending ? 'Generando…' : 'Generar backend Spring Boot'}</button><button className="toolbar-button justify-center" disabled={generate.isPending} onClick={() => generate.mutate()} type="button">{generate.isPending ? 'Generando…' : 'Generar frontend React Native'}</button></div> : <p className="mt-2 text-amber-700">El modelo lógico debe estar VALID para generar.</p>}</div>
    {history.isPending && <p className="mt-2 text-slate-500">Cargando historial…</p>}
    {!history.isPending && runs.length === 0 && <p className="mt-2 text-slate-500">Todavía no hay generaciones.</p>}
    <div className="mt-2 grid gap-2">{runs.map((run, index) => <article className="rounded border border-slate-200 bg-white p-2" key={run.id}>
      <div className="flex items-start justify-between gap-2"><div><strong>#{runs.length - index} · {run.status}</strong><p>ModelVersion v{run.modelVersionNumber} · LogicalModel {run.logicalModelId.slice(0, 8)}</p><p>{new Date(run.createdAt).toLocaleString()}</p></div><button className="text-indigo-700 underline" type="button" onClick={() => setSelectedId(run.id)}>Ver</button></div>
      {run.artifacts.map((artifact) => <p className="mt-1 font-mono text-slate-600" key={artifact.id}>{artifact.fileName} · {formatSize(artifact.sizeBytes)}</p>)}
    </article>)}</div>
    {(history.error || generate.error) && <p className="form-error mt-2">{getApiErrorMessage(history.error ?? generate.error)}</p>}
    {selectedId && <GenerationDetail run={selected.data} loading={selected.isPending} error={selected.error} canDownload={canGenerate} onClose={() => setSelectedId(null)} />}
  </section>
}

function GenerationDetail({ run, loading, error, canDownload, onClose }: { run?: GenerationRun; loading: boolean; error: unknown; canDownload: boolean; onClose: () => void }) {
  const [instructions, setInstructions] = useState(false)
  const download = useMutation({ mutationFn: async ({ runId, artifactId, fileName }: { runId: string; artifactId: string; fileName: string }) => {
    const blob=await logicalModelApi.downloadArtifact(runId,artifactId);const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=fileName;document.body.appendChild(link);link.click();link.remove();URL.revokeObjectURL(url)
  } })
  return <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/50 p-4" role="dialog" aria-modal="true" aria-label="Ver generación"><div className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-2xl bg-white p-6 text-sm shadow-xl">
    <div className="flex items-center justify-between"><h2 className="text-lg font-semibold">Ver generación</h2><button className="toolbar-button" type="button" onClick={onClose} aria-label="Cerrar"><X size={18} /></button></div>
    {loading && <p className="mt-4">Consultando estado persistido...</p>}{Boolean(error) && <p className="form-error mt-4">{getApiErrorMessage(error)}</p>}
    {run && <><div className={`mt-4 rounded border p-3 ${run.status === 'FAILED' ? 'border-rose-200 bg-rose-50' : terminal(run.status) ? 'border-emerald-200 bg-emerald-50' : 'border-indigo-200 bg-indigo-50'}`}><strong>{run.status}</strong><p>Etapa actual: {run.currentStage ?? 'En cola'}</p>{run.errorMessage && <p className="mt-1 text-rose-700">{run.errorMessage}</p>}</div>
      <h3 className="mb-2 mt-4 font-semibold">Origen</h3><dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1"><dt>ModelVersion</dt><dd>v{run.modelVersionNumber} · {run.modelVersionId}</dd><dt>LogicalModel</dt><dd className="break-all">{run.logicalModelId}</dd><dt>GenerationProfile</dt><dd>{run.generationProfileName} · {run.generationProfileId}</dd><dt>Generator</dt><dd>{run.generatorVersion}</dd></dl>
      <h3 className="mb-2 mt-4 font-semibold">Etapas</h3><div className="grid gap-2">{stages.map((stage) => { const result=run.tests.find((test) => test.testType === stage); const active=run.currentStage === stage && !terminal(run.status); return <details className="rounded border border-slate-200 p-2" key={stage}><summary className="cursor-pointer font-medium">{stage} · {result?.status ?? (active ? 'RUNNING' : 'PENDING')}</summary>{result?.outputLog && <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap rounded bg-slate-900 p-2 text-xs text-slate-100">{result.outputLog}</pre>}</details> })}</div>
      <h3 className="mb-2 mt-4 font-semibold">Artifacts</h3>{run.artifacts.length === 0 ? <p className="text-slate-500">El artifact aparecerá únicamente al completar PACKAGING.</p> : run.artifacts.map((artifact) => <div className="rounded border border-slate-200 p-2" key={artifact.id}><strong>{artifact.artifactType === 'REACT_NATIVE_ZIP' ? 'Frontend React Native' : 'Backend Spring Boot'}</strong><p>{artifact.fileName}</p><p>{formatSize(artifact.sizeBytes)}</p><p className="break-all font-mono text-xs">SHA-256: {artifact.checksum}</p>{run.status === 'SUCCESS' && canDownload && <button className="toolbar-button mt-2" disabled={download.isPending} onClick={() => download.mutate({ runId: run.id, artifactId: artifact.id, fileName: artifact.fileName })} type="button">{download.isPending ? 'Descargando...' : artifact.artifactType === 'REACT_NATIVE_ZIP' ? 'Descargar React Native' : 'Descargar backend Spring Boot'}</button>}</div>)}
      <button className="mt-3 text-indigo-700 underline" onClick={() => setInstructions((shown) => !shown)} type="button">Ver instrucciones</button>{instructions && <div className="mt-2 rounded border border-slate-200 bg-slate-50 p-3"><p>1. Descomprime el ZIP; ya contiene un <code>.env</code> de desarrollo.</p><p>2. Docker: <code>docker compose up -d</code>.</p><p>3. Windows: <code>.\run.ps1</code>.</p><p>4. Linux/macOS: <code>chmod +x run.sh mvnw &amp;&amp; ./run.sh</code>.</p><p>5. Para PostgreSQL local, edita <code>.env</code> antes de ejecutar el script.</p><p>6. Abre <code>http://localhost:8080/swagger-ui.html</code> o importa la colección de <code>postman/</code>.</p></div>}{download.error && <p className="form-error mt-2">{getApiErrorMessage(download.error)}</p>}</>}
  </div></div>
}
