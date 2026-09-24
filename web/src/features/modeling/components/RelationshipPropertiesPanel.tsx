import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { getApiErrorMessage } from '../../../api/apiError'
import { relationshipApi } from '../api/relationshipApi'
import { useEditorStore } from '../store/editorStore'
import type {
  UmlAggregationKind,
  UmlAssociation,
  UmlAssociationEnd,
  UmlClassifierView,
  UmlGeneralization,
  UmlRealization,
  UmlVisibility,
} from '../types/uml'

type MultiplicityPreset = 'ONE' | 'ZERO_ONE' | 'ZERO_MANY' | 'ONE_MANY' | 'CUSTOM'
type DirectedRelationship =
  | { kind: 'GENERALIZATION'; relationship: UmlGeneralization }
  | { kind: 'REALIZATION'; relationship: UmlRealization }

const presets = [
  { value: 'ONE', label: '1' },
  { value: 'ZERO_ONE', label: '0..1' },
  { value: 'ZERO_MANY', label: '0..*' },
  { value: 'ONE_MANY', label: '1..*' },
  { value: 'CUSTOM', label: 'Personalizada' },
]

const associationSchema = z.object({
  name: z.string().trim().max(180, 'Máximo 180 caracteres.'),
  classifierAId: z.string().min(1),
  roleNameA: z.string().trim().max(180),
  visibilityA: z.enum(['PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE']),
  presetA: z.enum(['ONE', 'ZERO_ONE', 'ZERO_MANY', 'ONE_MANY', 'CUSTOM']),
  lowerA: z.string(),
  upperA: z.string(),
  navigableA: z.boolean(),
  classifierBId: z.string().min(1),
  roleNameB: z.string().trim().max(180),
  visibilityB: z.enum(['PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE']),
  presetB: z.enum(['ONE', 'ZERO_ONE', 'ZERO_MANY', 'ONE_MANY', 'CUSTOM']),
  lowerB: z.string(),
  upperB: z.string(),
  navigableB: z.boolean(),
  relationshipType: z.enum(['NONE', 'SHARED', 'COMPOSITE']),
  wholeEnd: z.enum(['A', 'B']),
}).superRefine((values, context) => {
  const a = parseBounds(values.presetA, values.lowerA, values.upperA)
  const b = parseBounds(values.presetB, values.lowerB, values.upperB)
  if (!a) context.addIssue({ code: 'custom', path: ['lowerA'], message: 'Multiplicidad inválida.' })
  if (!b) context.addIssue({ code: 'custom', path: ['lowerB'], message: 'Multiplicidad inválida.' })
  if (values.relationshipType === 'COMPOSITE') {
    const whole = values.wholeEnd === 'A' ? a : b
    if (whole && (whole.upper === null || whole.upper > 1)) {
      context.addIssue({ code: 'custom', path: [values.wholeEnd === 'A' ? 'upperA' : 'upperB'], message: 'El extremo whole composite debe tener upper ≤ 1.' })
    }
  }
})

type AssociationValues = z.infer<typeof associationSchema>

export function AssociationProperties({
  modelId, association, classifiers, canEdit, onChanged,
}: {
  modelId: string
  association: UmlAssociation
  classifiers: UmlClassifierView[]
  canEdit: boolean
  onChanged: () => Promise<unknown>
}) {
  const selectElement = useEditorStore((state) => state.selectElement)
  const form = useForm<AssociationValues>({
    resolver: zodResolver(associationSchema),
    defaultValues: associationDefaults(association),
  })
  const presetA = useWatch({ control: form.control, name: 'presetA' })
  const presetB = useWatch({ control: form.control, name: 'presetB' })
  const relationshipType = useWatch({ control: form.control, name: 'relationshipType' })
  const save = useMutation({
    mutationFn: (values: AssociationValues) => {
      const boundsA = parseBounds(values.presetA, values.lowerA, values.upperA)!
      const boundsB = parseBounds(values.presetB, values.lowerB, values.upperB)!
      return relationshipApi.updateAssociation(modelId, association.id, {
        name: values.name || null,
        documentation: association.documentation,
        packageId: association.packageId,
        firstEnd: endInput(association.ends[0], values.classifierAId, values.roleNameA,
          boundsA, values.navigableA, values.wholeEnd === 'A' ? values.relationshipType : 'NONE', values.visibilityA),
        secondEnd: endInput(association.ends[1], values.classifierBId, values.roleNameB,
          boundsB, values.navigableB, values.wholeEnd === 'B' ? values.relationshipType : 'NONE', values.visibilityB),
      })
    },
    onSuccess: async (saved) => {
      form.reset(associationDefaults(saved))
      await onChanged()
    },
  })
  const remove = useMutation({
    mutationFn: () => relationshipApi.deleteAssociation(modelId, association.id),
    onSuccess: async () => {
      selectElement(null)
      await onChanged()
    },
  })

  return <form className="grid gap-3 p-4 text-sm" onSubmit={form.handleSubmit((values) => save.mutate(values))}>
    <h3 className="font-bold text-slate-800">{associationKind(association)}</h3>
    <fieldset className="grid gap-3" disabled={!canEdit}>
      <Field label="Nombre opcional"><input className="editor-input" {...form.register('name')} /></Field>
      <AssociationEndEditor
        classifiers={classifiers} label="Extremo A" prefix="A" preset={presetA}
        register={form.register}
      />
      <Field label="Tipo de relación"><select className="editor-input" {...form.register('relationshipType')}>
        <option value="NONE">NONE — asociación</option>
        <option value="SHARED">SHARED — agregación</option>
        <option value="COMPOSITE">COMPOSITE — composición</option>
      </select></Field>
      {relationshipType !== 'NONE' && <Field label="Extremo whole (diamante)"><select className="editor-input" {...form.register('wholeEnd')}>
        <option value="A">Extremo A</option>
        <option value="B">Extremo B</option>
      </select></Field>}
      <AssociationEndEditor
        classifiers={classifiers} label="Extremo B" prefix="B" preset={presetB}
        register={form.register}
      />
    </fieldset>
    {Object.values(form.formState.errors).length > 0 &&
      <p className="form-error">Revisa las multiplicidades y los campos indicados.</p>}
    {(save.error || remove.error) && <p className="form-error">{getApiErrorMessage(save.error || remove.error)}</p>}
    {canEdit && <div className="grid grid-cols-2 gap-2">
      <button className="primary-button !min-h-8 !py-1.5" disabled={save.isPending} type="submit">Guardar</button>
      <button className="danger-button" disabled={remove.isPending} onClick={() => {
        if (window.confirm('¿Eliminar esta relación?')) remove.mutate()
      }} type="button"><Trash2 size={14} /> Eliminar</button>
    </div>}
  </form>
}

export function DirectedRelationshipProperties({
  modelId, value, classifiers, canEdit, onChanged,
}: {
  modelId: string
  value: DirectedRelationship
  classifiers: UmlClassifierView[]
  canEdit: boolean
  onChanged: () => Promise<unknown>
}) {
  const selectElement = useEditorStore((state) => state.selectElement)
  const relation = value.relationship
  const isGeneralization = value.kind === 'GENERALIZATION'
  const sourceId = isGeneralization
    ? (relation as UmlGeneralization).specificClassifierId
    : (relation as UmlRealization).clientClassifierId
  const targetId = isGeneralization
    ? (relation as UmlGeneralization).generalClassifierId
    : (relation as UmlRealization).supplierClassifierId
  const remove = useMutation({
    mutationFn: () => isGeneralization
      ? relationshipApi.deleteGeneralization(modelId, relation.id)
      : relationshipApi.deleteRealization(modelId, relation.id),
    onSuccess: async () => {
      selectElement(null)
      await onChanged()
    },
  })
  return <div className="grid gap-3 p-4 text-sm">
    <h3 className="font-bold text-slate-800">{isGeneralization ? 'Generalización' : 'Realización'}</h3>
    <PropertyValue label={isGeneralization ? 'Specific (origen)' : 'Client (origen)'} value={classifierName(sourceId, classifiers)} />
    <PropertyValue label={isGeneralization ? 'General (destino)' : 'Supplier Interface (destino)'} value={classifierName(targetId, classifiers)} />
    {remove.error && <p className="form-error">{getApiErrorMessage(remove.error)}</p>}
    {canEdit && <button className="danger-button" disabled={remove.isPending} onClick={() => {
      if (window.confirm('¿Eliminar esta relación?')) remove.mutate()
    }} type="button"><Trash2 size={14} /> Eliminar relación</button>}
  </div>
}

function AssociationEndEditor({ classifiers, label, prefix, preset, register }: {
  classifiers: UmlClassifierView[]
  label: string
  prefix: 'A' | 'B'
  preset: MultiplicityPreset
  register: ReturnType<typeof useForm<AssociationValues>>['register']
}) {
  return <section className="grid gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3">
    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-600">{label}</h4>
    <Field label="Clase / Interfaz"><select className="editor-input" {...register(`classifier${prefix}Id`)}>
      {classifiers.map((classifier) => <option key={classifier.id} value={classifier.id}>{classifier.name}</option>)}
    </select></Field>
    <Field label="roleName"><input className="editor-input" {...register(`roleName${prefix}`)} /></Field>
    <Field label="Visibilidad"><select className="editor-input" {...register(`visibility${prefix}`)}>
      {(['PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE'] as const).map((visibility) => <option key={visibility} value={visibility}>{visibility}</option>)}
    </select></Field>
    <Field label="Multiplicidad"><select className="editor-input" {...register(`preset${prefix}`)}>
      {presets.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
    </select></Field>
    {preset === 'CUSTOM' && <div className="grid grid-cols-2 gap-2">
      <Field label="Lower"><input className="editor-input" min="0" type="number" {...register(`lower${prefix}`)} /></Field>
      <Field label="Upper"><input className="editor-input" placeholder="*" {...register(`upper${prefix}`)} /></Field>
    </div>}
    <label className="flex items-center gap-2 text-xs text-slate-600"><input type="checkbox" {...register(`navigable${prefix}`)} />Navegable</label>
  </section>
}

function associationDefaults(association: UmlAssociation): AssociationValues {
  const [a, b] = association.ends
  return {
    name: association.name ?? '',
    classifierAId: a.classifierId,
    roleNameA: a.roleName ?? '',
    visibilityA: a.visibility,
    presetA: presetFor(a.lowerBound, a.upperBound),
    lowerA: String(a.lowerBound),
    upperA: a.upperBound === null ? '*' : String(a.upperBound),
    navigableA: a.navigable,
    classifierBId: b.classifierId,
    roleNameB: b.roleName ?? '',
    visibilityB: b.visibility,
    presetB: presetFor(b.lowerBound, b.upperBound),
    lowerB: String(b.lowerBound),
    upperB: b.upperBound === null ? '*' : String(b.upperBound),
    navigableB: b.navigable,
    relationshipType: a.aggregation !== 'NONE' ? a.aggregation : b.aggregation,
    wholeEnd: b.aggregation !== 'NONE' ? 'B' : 'A',
  }
}

function endInput(
  original: UmlAssociationEnd,
  classifierId: string,
  roleName: string,
  bounds: { lower: number; upper: number | null },
  navigable: boolean,
  aggregation: UmlAggregationKind,
  visibility: UmlVisibility,
) {
  return {
    roleName: roleName || null,
    classifierId,
    visibility,
    lowerBound: bounds.lower,
    upperBound: bounds.upper,
    navigable,
    aggregation,
    ownership: original.ownership,
  }
}

function parseBounds(preset: MultiplicityPreset, lowerText: string, upperText: string) {
  const known = {
    ONE: { lower: 1, upper: 1 },
    ZERO_ONE: { lower: 0, upper: 1 },
    ZERO_MANY: { lower: 0, upper: null },
    ONE_MANY: { lower: 1, upper: null },
  } as const
  if (preset !== 'CUSTOM') return known[preset]
  if (!/^\d+$/.test(lowerText.trim()) || !/^(\d+|\*)$/.test(upperText.trim())) return null
  const lower = Number(lowerText)
  const upper = upperText.trim() === '*' ? null : Number(upperText)
  return upper !== null && upper < lower ? null : { lower, upper }
}

function presetFor(lower: number, upper: number | null): MultiplicityPreset {
  if (lower === 1 && upper === 1) return 'ONE'
  if (lower === 0 && upper === 1) return 'ZERO_ONE'
  if (lower === 0 && upper === null) return 'ZERO_MANY'
  if (lower === 1 && upper === null) return 'ONE_MANY'
  return 'CUSTOM'
}

function associationKind(association: UmlAssociation) {
  const aggregations = association.ends.map((end) => end.aggregation)
  if (aggregations.includes('COMPOSITE')) return 'Composición'
  if (aggregations.includes('SHARED')) return 'Agregación'
  return 'Asociación'
}

function classifierName(id: string, classifiers: UmlClassifierView[]) {
  return classifiers.find((classifier) => classifier.id === id)?.name ?? id
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="editor-label">{label}{children}</label>
}

function PropertyValue({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return <div><dt className="mb-1 text-xs font-semibold uppercase tracking-wider text-slate-400">{label}</dt><dd className={`break-words text-slate-700 ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd></div>
}
