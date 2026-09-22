import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Microscope, X } from 'lucide-react'
import { getApiErrorMessage } from '../../../api/apiError'
import { normalizationApi, type NormalizationRun, type NormalizationStatus } from '../api/normalizationApi'

const tone = (status: NormalizationStatus) => status === 'PASS'
  ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
  : status === 'WARNING' ? 'border-amber-200 bg-amber-50 text-amber-800'
    : status === 'FAIL' ? 'border-rose-200 bg-rose-50 text-rose-800' : 'border-slate-200 bg-slate-50 text-slate-600'

export function NormalizationPanel({ logicalModelId, canAnalyze, onLocate }: {
  logicalModelId: string; canAnalyze: boolean; onLocate: (id: string) => void
}) {
  const client = useQueryClient()
  const [selected, setSelected] = useState<NormalizationRun | null>(null)
  const runs = useQuery({ queryKey: ['normalization-runs', logicalModelId], queryFn: () => normalizationApi.list(logicalModelId) })
  const analyze = useMutation({ mutationFn: () => normalizationApi.analyze(logicalModelId), onSuccess: async (run) => {
    setSelected(run); await client.invalidateQueries({ queryKey: ['normalization-runs', logicalModelId] })
  } })
  const latest = runs.data?.[0]
  return <section className="mt-3 rounded-lg border border-slate-200 p-3" aria-label="Normalización">
    <div className="flex items-center justify-between gap-2">
      <h5 className="flex items-center gap-1 font-semibold uppercase text-slate-600"><Microscope size={14} />Normalización</h5>
      {canAnalyze && <button className="toolbar-button" disabled={analyze.isPending} onClick={() => analyze.mutate()} type="button">
        {analyze.isPending ? 'Analizando…' : 'Analizar normalización'}
      </button>}
    </div>
    {runs.isPending && <p className="mt-2 text-slate-500">Consultando análisis…</p>}
    {!runs.isPending && !latest && <p className="mt-2 text-slate-500">No analizado</p>}
    {latest && <div className="mt-2">
      <div className="flex flex-wrap gap-1">{latest.forms.map((form) => <span className={`rounded border px-2 py-1 ${tone(form.status)}`} key={form.normalForm}>{form.normalForm} {form.status}</span>)}</div>
      <p className={`mt-2 inline-block rounded border px-2 py-1 font-semibold ${tone(latest.status)}`}>
        Resultado: {latest.status === 'PASS' ? 'PASS' : latest.status === 'UNKNOWN' ? 'DESCONOCIDO' : 'REVIEW REQUIRED'}
      </p>
      <p className="mt-1 text-slate-500">{new Date(latest.createdAt).toLocaleString()} · el resultado no bloquea la generación.</p>
      <button className="mt-1 text-indigo-700 underline" onClick={() => setSelected(latest)} type="button">Ver análisis</button>
    </div>}
    {runs.error && <p className="form-error mt-2">{getApiErrorMessage(runs.error)}</p>}
    {analyze.error && <p className="form-error mt-2">{getApiErrorMessage(analyze.error)}</p>}
    {selected && <NormalizationDialog run={selected} onClose={() => setSelected(null)} onLocate={(id) => { onLocate(id); setSelected(null) }} />}
  </section>
}

function NormalizationDialog({ run, onClose, onLocate }: { run: NormalizationRun; onClose: () => void; onLocate: (id: string) => void }) {
  return <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/50 p-4" role="dialog" aria-modal="true" aria-label="Análisis de normalización">
    <div className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-2xl bg-white p-6 text-sm shadow-xl">
      <div className="flex items-center justify-between"><h2 className="text-lg font-semibold">Normalización · {run.status}</h2><button className="toolbar-button" onClick={onClose} type="button" aria-label="Cerrar"><X size={18} /></button></div>
      <p className="mt-1 text-slate-500">LogicalModel {run.logicalModelId.slice(0, 8)} · {new Date(run.createdAt).toLocaleString()}</p>
      <div className="my-4 grid gap-2 sm:grid-cols-3">{run.forms.map((form) => <section className={`rounded border p-3 ${tone(form.status)}`} key={form.normalForm}><strong>{form.normalForm} {form.status}</strong><p className="mt-1">{form.statement}</p></section>)}</div>
      {run.issues.length === 0 && <p className="rounded border border-emerald-200 bg-emerald-50 p-3 text-emerald-800">No se detectaron problemas respecto a las dependencias conocidas por CASEGen.</p>}
      <div className="grid gap-3">{run.issues.map((issue) => <article className={`rounded border p-4 ${issue.severity === 'ERROR' ? 'border-rose-200 bg-rose-50' : 'border-amber-200 bg-amber-50'}`} key={issue.id}>
        <strong>{issue.normalForm} · {issue.severity} · {issue.code}</strong>
        <p className="mt-1"><b>Tabla:</b> {issue.tableName ?? 'No disponible'}</p>
        <p><b>Columnas:</b> {issue.metadata.columns?.join(', ') || issue.columnName || 'No disponible'}</p>
        {typeof issue.metadata.dependency === 'string' && <p><b>Dependencia observada:</b> {issue.metadata.dependency}</p>}
        <p className="mt-2">{issue.message}</p><p className="mt-2"><b>Recomendación:</b> {issue.recommendation}</p>
        {issue.sourceElementId && <button className="mt-2 text-indigo-700 underline" onClick={() => onLocate(issue.sourceElementId!)} type="button">Ir al elemento UML</button>}
      </article>)}</div>
    </div>
  </div>
}
