import type { ElementMapping, GenerationProfile, VersionSnapshot } from '../api/logicalModelApi'

export type MappingDraft = { classifierId: string; target: string; idPropertyId: string; tableName: string; config: Record<string, unknown> }
export type Property = VersionSnapshot['semanticSnapshot']['properties'][number]
export function object(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {} }
export function namesFor(version: VersionSnapshot) { return new Map(version.semanticSnapshot.elements.map((e) => [e.id, e.name || 'Elemento sin nombre'])) }
export function identifiersFor(version: VersionSnapshot, classifierId: string): Property[] {
  const owners = new Set([classifierId]); const pending = [classifierId]
  while (pending.length) {
    const next = pending.pop()!
    for (const g of version.semanticSnapshot.generalizations ?? []) if (g.specific_classifier_id === next && !owners.has(g.general_classifier_id)) {
      owners.add(g.general_classifier_id); pending.push(g.general_classifier_id)
    }
  }
  const ends = new Set(version.semanticSnapshot.associationEnds?.map((e) => e.property_id))
  return version.semanticSnapshot.properties.filter((p) => owners.has(p.owner_classifier_id ?? '') && !ends.has(p.element_id)
    && ['STRING', 'INTEGER', 'UNLIMITED_NATURAL'].includes(p.primitive_type ?? '') && p.lower_bound === 1 && p.upper_bound === 1 && !p.is_static && !p.is_derived)
}
export function mappingDrafts(version: VersionSnapshot, mappings: ElementMapping[]): MappingDraft[] {
  const names = namesFor(version)
  return version.semanticSnapshot.classifiers.map((c) => {
    const saved = mappings.find((m) => m.umlElementId === c.element_id)
    const candidates = identifiersFor(version, c.element_id)
    const ownIds = candidates.filter((p) => p.owner_classifier_id === c.element_id && names.get(p.element_id)?.toLowerCase() === 'id')
    const suggested = ownIds.length ? ownIds : candidates.filter((p) => names.get(p.element_id)?.toLowerCase() === 'id')
    return { classifierId: c.element_id, target: saved?.targetKind ?? (c.kind === 'CLASS' ? 'ENTITY' : 'JAVA_INTERFACE'),
      idPropertyId: String(saved?.configJson.idPropertyId ?? (suggested.length === 1 ? suggested[0].element_id : '')),
      tableName: String(saved?.configJson.tableName ?? ''), config: saved?.configJson ?? {} }
  })
}
export const snake = (value: string) => value.replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2').replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
export interface AssociationDecision {
  id: string; title: string; description: string; classifierA: string; classifierB: string
  ambiguous: boolean; self: boolean; automaticOwner: string; referencedEndOrder: number
}
export function associationDecisions(version: VersionSnapshot, drafts: MappingDraft[], profileConfig: Record<string, unknown>): AssociationDecision[] {
  const names = namesFor(version); const properties = new Map(version.semanticSnapshot.properties.map((p) => [p.element_id, p]))
  const config = object(profileConfig.associations)
  const tableName = (id: string) => snake(drafts.find((d) => d.classifierId === id)?.tableName || names.get(id) || 'tabla')
  return (version.semanticSnapshot.associations ?? []).flatMap((association) => {
    const ends = (version.semanticSnapshot.associationEnds ?? []).filter((e) => e.association_id === association.element_id).sort((a, b) => a.member_order - b.member_order)
    if (ends.length !== 2) return [] // Authoritative preview reports malformed version data.
    const pa = properties.get(ends[0].property_id), pb = properties.get(ends[1].property_id)
    if (!pa || !pb) return []
    const a = pa.type_classifier_id ?? '', b = pb.type_classifier_id ?? ''
    const manyA = pa.upper_bound === null || pa.upper_bound > 1, manyB = pb.upper_bound === null || pb.upper_bound > 1
    const cfg = object(config[association.element_id]); const self = a === b
    let owner = '', target = '', referencedEndOrder = 0, description = '', ambiguous = false
    const wholeA = ends[0].aggregation === 'COMPOSITE', wholeB = ends[1].aggregation === 'COMPOSITE'
    if (drafts.some((d) => (d.classifierId === a || d.classifierId === b) && d.target === 'IGNORE')) description = 'No se persiste: un extremo está marcado IGNORE.'
    else if (wholeA || wholeB) { owner = wholeA ? b : a; target = wholeA ? a : b; referencedEndOrder = wholeA ? 1 : 2 }
    else if (manyA && manyB) description = `Join table automática ${snake(String(cfg.joinTableName || version.semanticSnapshot.elements.find((e) => e.id === association.element_id)?.name || `${tableName(a)}_${tableName(b)}`))} con dos FK y PK compuesta.`
    else if (manyA || manyB) { owner = manyA ? a : b; target = manyA ? b : a; referencedEndOrder = manyA ? 2 : 1 }
    else {
      const ownedA = ends[0].ownership === 'CLASSIFIER' ? pa.owner_classifier_id : null
      const ownedB = ends[1].ownership === 'CLASSIFIER' ? pb.owner_classifier_id : null
      const inferred = ownedA && !ownedB ? ownedA : ownedB && !ownedA ? ownedB : ''
      ambiguous = !inferred || self
      owner = String(cfg.fkOwnerClassifierId || inferred)
      if (owner) { target = owner === a ? b : a; referencedEndOrder = self ? Number(cfg.referencedEndOrder ?? 0) : owner === b ? 1 : 2 }
    }
    if (owner && target && (!self || referencedEndOrder)) {
      const end = referencedEndOrder === 1 ? pa : pb
      const role = version.semanticSnapshot.elements.find((e) => e.id === end.element_id)?.name
      const targetMapping = drafts.find((d) => d.classifierId === target)
      const pkName = names.get(targetMapping?.idPropertyId ?? '') || 'id'
      const pkConfig = object(object(targetMapping?.config.properties)[targetMapping?.idPropertyId ?? ''])
      const column = snake(String(cfg.fkColumnName || `${role || tableName(target)}_${String(pkConfig.columnName || pkName)}`))
      description = `FK ${ambiguous ? 'configurada' : 'automática'} ${tableName(owner)}.${column}${!manyA && !manyB ? ' + UNIQUE' : ''}${wholeA || wholeB ? ' · part → whole · CASCADE' : ''}`
    } else if (ambiguous) description = 'Falta elegir Propietario de FK para esta relación 1:1.'
    const card = manyA && manyB ? 'N:M' : manyA ? 'N:1' : manyB ? '1:N' : '1:1'
    return [{ id: association.element_id, title: `${names.get(a)} ${card} ${names.get(b)}`, description,
      classifierA: a, classifierB: b, ambiguous, self, automaticOwner: ambiguous ? '' : owner, referencedEndOrder }]
  })
}
export function profileDefaults(profile?: GenerationProfile): Omit<GenerationProfile, 'id' | 'modelId'> {
  return { name: profile?.name ?? 'Backend', target: 'SPRING_BOOT', database: 'POSTGRESQL', javaVersion: profile?.javaVersion ?? 21,
    springBootVersion: profile?.springBootVersion ?? '4.1.1', groupId: profile?.groupId ?? 'com.casegen.generated', artifactId: profile?.artifactId ?? 'backend',
    basePackage: profile?.basePackage ?? 'com.casegen.generated', configJson: profile?.configJson ?? { inheritanceStrategy: 'JOINED' } }
}
