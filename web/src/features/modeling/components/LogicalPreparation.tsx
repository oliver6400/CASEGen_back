import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Database } from 'lucide-react'
import { getApiErrorMessage } from '../../../api/apiError'
import { logicalModelApi, type ElementMapping, type GenerationProfile, type LogicalPreview, type VersionSnapshot } from '../api/logicalModelApi'
import { associationDecisions, identifiersFor, mappingDrafts, namesFor, object, profileDefaults } from '../lib/logicalPreparation'
import { WorkspaceDrawer } from './WorkspaceDrawer'
import { OPEN_PREPARATION_PANEL_EVENT, type PreparationPanel } from './logicalPreparationPanels'

export function LogicalPreparation({ modelId, profile, version, mappings, preview, previewBusy, previewError, building, onSaved, onBuild }: {
  modelId: string; profile?: GenerationProfile; version: VersionSnapshot; mappings: ElementMapping[]; preview?: LogicalPreview
  previewBusy: boolean; previewError: unknown; building: boolean; onSaved: (id?: string) => Promise<void>; onBuild: () => void
}) {
  const [fields, setFields] = useState(profileDefaults(profile))
  const [drafts, setDrafts] = useState(mappingDrafts(version, mappings))
  const [advanced, setAdvanced] = useState(JSON.stringify(fields.configJson, null, 2))
  const [owners, setOwners] = useState<Record<string, string>>({})
  const [endOrders, setEndOrders] = useState<Record<string, number>>({})
  const [strategy, setStrategy] = useState(String(fields.configJson.inheritanceStrategy ?? 'JOINED'))
  const [openPanel, setOpenPanel] = useState<PreparationPanel | null>(null)
  useEffect(() => {
    const openRequestedPanel = (event: Event) => setOpenPanel((event as CustomEvent<PreparationPanel>).detail)
    window.addEventListener(OPEN_PREPARATION_PANEL_EVENT, openRequestedPanel)
    return () => window.removeEventListener(OPEN_PREPARATION_PANEL_EVENT, openRequestedPanel)
  }, [])
  const names = namesFor(version)
  let advancedError = ''
  let configJson: Record<string, unknown> = {}
  try { const parsed: unknown = JSON.parse(advanced); if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(); configJson = object(parsed) }
  catch { advancedError = 'Configuración avanzada: el JSON debe ser un objeto válido.' }
  const associations = { ...object(configJson.associations) }
  for (const [id, owner] of Object.entries(owners)) {
    const config = { ...object(associations[id]) }
    if (owner) config.fkOwnerClassifierId = owner; else delete config.fkOwnerClassifierId
    associations[id] = config
  }
  for (const [id, order] of Object.entries(endOrders)) associations[id] = { ...object(associations[id]), referencedEndOrder: order }
  configJson = { ...configJson, inheritanceStrategy: strategy, ...(Object.keys(associations).length ? { associations } : {}) }
  const decisions = associationDecisions(version, drafts, configJson)
  const payload = { ...fields, configJson }
  const mappingPayloads = drafts.map((draft) => {
    const config = { ...draft.config }
    if (draft.target === 'ENTITY') { config.idPropertyId = draft.idPropertyId; if (draft.tableName.trim()) config.tableName = draft.tableName.trim(); else delete config.tableName }
    return { umlElementId: draft.classifierId, targetKind: draft.target, configJson: config }
  })
  const dirty = !profile || JSON.stringify(payload) !== JSON.stringify(profileDefaults(profile)) || mappingPayloads.some((draft) => {
    const saved = mappings.find((m) => m.umlElementId === draft.umlElementId)
    return !saved || saved.targetKind !== draft.targetKind || JSON.stringify(saved.configJson) !== JSON.stringify(draft.configJson)
  })
  const missing: string[] = []
  if (!fields.name.trim()) missing.push('Perfil: falta nombre.')
  const coordinate = /^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*$/
  if (!coordinate.test(fields.groupId)) missing.push('Perfil: groupId no válido.')
  if (!coordinate.test(fields.basePackage)) missing.push('Perfil: basePackage no válido.')
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(fields.artifactId)) missing.push('Perfil: artifactId no válido.')
  if (!Number.isInteger(fields.javaVersion) || fields.javaVersion < 21) missing.push('Perfil: Java version debe ser un entero >= 21.')
  if (!/^\d+\.\d+\.\d+(?:[-.][A-Za-z0-9.-]+)?$/.test(fields.springBootVersion)) missing.push('Perfil: Spring Boot version no válida.')
  if (strategy !== 'JOINED') missing.push('Herencia: V9 solo admite JOINED.')
  if (advancedError) missing.push(advancedError)
  for (const draft of drafts) {
    const kind = version.semanticSnapshot.classifiers.find((c) => c.element_id === draft.classifierId)?.kind
    if (!['ENTITY', 'JAVA_INTERFACE', 'IGNORE'].includes(draft.target) || (draft.target === 'ENTITY' && kind !== 'CLASS') || (draft.target === 'JAVA_INTERFACE' && kind !== 'INTERFACE')) missing.push(`${names.get(draft.classifierId)}: selecciona un mapping compatible.`)
    if (draft.target === 'ENTITY' && !identifiersFor(version, draft.classifierId).some((p) => p.element_id === draft.idPropertyId)) missing.push(`${names.get(draft.classifierId)}: falta seleccionar un Identificador válido (obligatorio y escalar).`)
  }
  if (!drafts.some((draft) => draft.target === 'ENTITY')) missing.push('Selecciona al menos una clase como ENTITY.')
  for (const decision of decisions.filter((d) => d.ambiguous)) {
    const config = object(associations[decision.id]); const owner = String(config.fkOwnerClassifierId ?? '')
    if (![decision.classifierA, decision.classifierB].includes(owner)) missing.push(`${decision.title}: falta elegir Propietario de FK.`)
    if (decision.self && ![1, 2].includes(Number(config.referencedEndOrder))) missing.push(`${decision.title}: falta elegir el extremo referenciado.`)
  }
  const save = useMutation({ mutationFn: async () => {
    const saved = await logicalModelApi.saveProfile(modelId, payload, profile?.id)
    for (const mapping of mappingPayloads) {
      const existing = mappings.find((m) => m.umlElementId === mapping.umlElementId)
      if (!existing || existing.targetKind !== mapping.targetKind || JSON.stringify(existing.configJson) !== JSON.stringify(mapping.configJson))
        await logicalModelApi.saveMapping(modelId, saved.id, mapping, existing?.id)
    }
    return saved.id
  }, onSettled: (id) => onSaved(id) })
  const backendErrors = !dirty ? preview?.issues.filter((issue) => issue.severity === 'ERROR') ?? [] : []
  const ready = !dirty && !missing.length && !!preview && !backendErrors.length && !previewBusy && !previewError
  const busy = building || save.isPending
  const update = (id: string, patch: Partial<typeof drafts[number]>) => setDrafts((current) => current.map((d) => d.classifierId === id ? { ...d, ...patch } : d))
  const savePreparationButton = () => <button className="toolbar-button mt-4 w-full justify-center" disabled={busy || !!missing.length || !dirty} onClick={() => save.mutate()} type="button">{save.isPending ? 'Guardando…' : 'Guardar preparación'}</button>
  return <div className="mt-3 grid gap-3">
    <section><h4 className="mb-1 font-semibold text-slate-600">Configuración de generación</h4><button className="toolbar-button w-full justify-center" onClick={() => setOpenPanel('PROFILE')} type="button">Perfil de generación</button></section>
    <section><h4 className="mb-1 font-semibold text-slate-600">Mapeo</h4><button className="toolbar-button w-full justify-center" onClick={() => setOpenPanel('MAPPINGS')} type="button">Mapeo de classifiers</button></section>
    <section><h4 className="mb-1 font-semibold text-slate-600">Relaciones</h4><button className="toolbar-button w-full justify-center" onClick={() => setOpenPanel('RELATIONSHIPS')} type="button">Decisiones automáticas</button></section>
    <section><h4 className="mb-1 font-semibold text-slate-600">Resultado</h4><button className="toolbar-button w-full justify-center" onClick={() => setOpenPanel('SUMMARY')} type="button">Resumen previo</button></section>

    <WorkspaceDrawer open={openPanel === 'PROFILE'} title="Perfil de generación" onClose={() => setOpenPanel(null)}><fieldset disabled={busy} className="grid gap-3">
      <label className="editor-label">Nombre<input className="editor-input" maxLength={180} value={fields.name} onChange={(e) => setFields({ ...fields, name: e.target.value })} /></label>
      <label className="editor-label">Target<select className="editor-input" value="SPRING_BOOT" disabled><option>SPRING_BOOT</option></select></label>
      <label className="editor-label">Database<select className="editor-input" value="POSTGRESQL" disabled><option>POSTGRESQL</option></select></label>
      <label className="editor-label">Java version<input type="number" min={21} step={1} className="editor-input" value={fields.javaVersion} onChange={(e) => setFields({ ...fields, javaVersion: Number(e.target.value) })} /></label>
      <label className="editor-label">Spring Boot version<input className="editor-input" maxLength={40} value={fields.springBootVersion} onChange={(e) => setFields({ ...fields, springBootVersion: e.target.value })} /></label>
      {(['groupId', 'artifactId', 'basePackage'] as const).map((field) => <label className="editor-label" key={field}>{field}<input className="editor-input" maxLength={180} value={fields[field]} onChange={(e) => setFields({ ...fields, [field]: e.target.value })} /></label>)}
      <label className="editor-label">Inheritance strategy<select className="editor-input" value={strategy} onChange={(e) => setStrategy(e.target.value)}><option>JOINED</option>{strategy !== 'JOINED' && <option>{strategy} (no soportada)</option>}</select></label>
    </fieldset>{savePreparationButton()}</WorkspaceDrawer>

    <WorkspaceDrawer open={openPanel === 'MAPPINGS'} title="Mapeo de classifiers" onClose={() => setOpenPanel(null)}><fieldset disabled={busy} className="grid gap-3">
      {drafts.map((draft) => {
        const kind = version.semanticSnapshot.classifiers.find((c) => c.element_id === draft.classifierId)?.kind
        const candidates = identifiersFor(version, draft.classifierId)
        return <div key={draft.classifierId} className="grid gap-2 rounded border border-slate-200 p-2">
          <strong>{names.get(draft.classifierId)}</strong>
          <select className="editor-input" aria-label={`Mapping de ${names.get(draft.classifierId)}`} value={draft.target} onChange={(e) => update(draft.classifierId, { target: e.target.value })}>
            {kind === 'CLASS' ? <option>ENTITY</option> : <option>JAVA_INTERFACE</option>}<option>IGNORE</option>
            {!['ENTITY', 'JAVA_INTERFACE', 'IGNORE'].includes(draft.target) && <option>{draft.target} (no soportado)</option>}
          </select>
          {draft.target === 'ENTITY' && <label className="editor-label">Identificador<select className="editor-input" value={draft.idPropertyId} onChange={(e) => update(draft.classifierId, { idPropertyId: e.target.value })}>
            <option value="">Selecciona una Property de la clase</option>{candidates.map((p) => <option key={p.element_id} value={p.element_id}>{p.owner_classifier_id !== draft.classifierId ? `${names.get(p.owner_classifier_id ?? '')}.` : ''}{names.get(p.element_id)}{p.owner_classifier_id !== draft.classifierId ? ' (heredado)' : ''}</option>)}
          </select>{!candidates.length && <span className="text-amber-700">La versión no contiene un identificador escalar válido. Añádelo al UML y crea una nueva versión.</span>}</label>}
        </div>
      })}
    </fieldset>{savePreparationButton()}</WorkspaceDrawer>

    <WorkspaceDrawer open={openPanel === 'RELATIONSHIPS'} title="Decisiones automáticas" onClose={() => setOpenPanel(null)}><fieldset disabled={busy} className="grid gap-3">
      {decisions.map((decision) => <div className="rounded border border-slate-200 p-2" key={decision.id}>
        <strong>{decision.title}</strong><p className="mt-1 text-slate-600">{decision.description}</p>
        {decision.ambiguous && <label className="editor-label mt-2">Propietario de FK<select className="editor-input" value={String(object(associations[decision.id]).fkOwnerClassifierId ?? '')} onChange={(e) => setOwners({ ...owners, [decision.id]: e.target.value })}>
          <option value="">Elegir lado dependiente</option>{[...new Set([decision.classifierA, decision.classifierB])].map((id) => <option key={id} value={id}>{names.get(id)}</option>)}
        </select></label>}
        {decision.ambiguous && decision.self && <label className="editor-label mt-2">Extremo referenciado<select className="editor-input" value={Number(object(associations[decision.id]).referencedEndOrder ?? 0)} onChange={(e) => setEndOrders({ ...endOrders, [decision.id]: Number(e.target.value) })}><option value={0}>Selecciona extremo</option><option value={1}>Extremo A</option><option value={2}>Extremo B</option></select></label>}
      </div>)}
    </fieldset>
    <details className="mt-4"><summary className="cursor-pointer text-indigo-700">Configuración avanzada</summary>
      <p className="mt-2 text-slate-500">Overrides opcionales. La preparación normal no requiere JSON ni UUID.</p>
      <textarea className="editor-input mt-2 min-h-32 font-mono" aria-label="Configuración avanzada JSON" disabled={busy} value={advanced} onChange={(e) => setAdvanced(e.target.value)} />
      {decisions.map((d) => <div key={d.id} className="mt-2 grid gap-2"><strong>{d.title}</strong>{(['fkColumnName', 'joinTableName'] as const).map((field) => <label className="editor-label" key={field}>{field}<input className="editor-input" disabled={busy || !!advancedError} value={String(object(associations[d.id])[field] ?? '')} onChange={(e) => {
        const entry = { ...object(associations[d.id]) }; if (e.target.value) entry[field] = e.target.value; else delete entry[field]
        setAdvanced(JSON.stringify({ ...configJson, associations: { ...associations, [d.id]: entry } }, null, 2))
      }} /></label>)}</div>)}
      {drafts.filter((d) => d.target === 'ENTITY').map((d) => <label className="editor-label mt-2" key={d.classifierId}>Tabla de {names.get(d.classifierId)}<input className="editor-input" disabled={busy} value={d.tableName} onChange={(e) => update(d.classifierId, { tableName: e.target.value })} /></label>)}
    </details>{savePreparationButton()}</WorkspaceDrawer>

    <WorkspaceDrawer open={openPanel === 'SUMMARY'} title="Resumen previo" onClose={() => setOpenPanel(null)}><div className="rounded border border-slate-200 bg-slate-50 p-3">
      {drafts.map((d) => <p className="mt-1" key={d.classifierId}>{names.get(d.classifierId)} → {d.target}{d.target === 'ENTITY' ? `, PK=${names.get(d.idPropertyId) ?? 'pendiente'}` : ''}</p>)}
      {decisions.map((d) => <p className="mt-1" key={d.id}>{d.title} → {d.description}</p>)}
    </div></WorkspaceDrawer>
    {missing.map((message, i) => <p className="text-amber-700" key={i}>{message}</p>)}
    {!missing.length && dirty && <p className="text-amber-700">Guarda la preparación sugerida para comprobarla con V9.</p>}
    {backendErrors.map((issue, i) => <p className="form-error" key={i}>{names.get(issue.sourceElementId ?? '') || 'Modelo'}: {issue.message}</p>)}
    {!dirty && previewBusy && <p className="text-slate-500">Comprobando decisiones con el transformador V9…</p>}
    {ready && <p className="text-emerald-700">✓ Preparación completa. Listo para construir modelo lógico.</p>}
    <button className="toolbar-button" disabled={busy || !ready} onClick={onBuild} type="button"><Database size={14} />{building ? 'Construyendo…' : 'Construir modelo lógico'}</button>
    {[save.error, !dirty ? previewError : null].filter(Boolean).map((error, i) => <p className="form-error" key={i}>{getApiErrorMessage(error)}</p>)}
  </div>
}
