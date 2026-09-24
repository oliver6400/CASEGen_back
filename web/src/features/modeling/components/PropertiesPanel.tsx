import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Plus, SlidersHorizontal } from 'lucide-react'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { getApiErrorMessage } from '../../../api/apiError'
import { classifierApi } from '../api/classifierApi'
import { diagramApi } from '../api/diagramApi'
import { featureApi } from '../api/featureApi'
import { useEditorStore } from '../store/editorStore'
import type { ClassDiagram, UmlClassifierView, UmlOperationView, UmlParameter, UmlParameterDirection, UmlPrimitiveType, UmlProperty, UmlRelationships, UmlTypeKind, UmlVisibility } from '../types/uml'
import { AssociationProperties, DirectedRelationshipProperties } from './RelationshipPropertiesPanel'

type SelectedElement = UmlClassifierView | UmlProperty | UmlOperationView | UmlParameter
const visibilities: UmlVisibility[] = ['PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE']
const primitiveTypes: UmlPrimitiveType[] = ['STRING', 'INTEGER', 'BOOLEAN', 'REAL', 'UNLIMITED_NATURAL']
const typeKindOptions = [{ value: 'PRIMITIVE', label: 'Primitivo' }, { value: 'CLASSIFIER', label: 'Clase / Interfaz' }]
const primitiveOptions = primitiveTypes.map((value) => ({ value, label: primitiveDisplayName(value) }))

interface PropertiesPanelProps {
  modelId: string
  diagram: ClassDiagram
  classifiers: UmlClassifierView[]
  relationships: UmlRelationships
  canEdit: boolean
  onSemanticChange: () => Promise<unknown>
  onDiagramChange: () => Promise<unknown>
}

export function PropertiesPanel({ modelId, diagram, classifiers, relationships, canEdit, onSemanticChange, onDiagramChange }: PropertiesPanelProps) {
  const selectedElementId = useEditorStore((state) => state.selectedElementId)
  const setEditing = useEditorStore((state) => state.setEditing)
  const elements: SelectedElement[] = classifiers.flatMap((classifier) => [classifier, ...classifier.properties, ...classifier.operations, ...classifier.operations.flatMap((operation) => operation.parameters)])
  const selected = elements.find((element) => element.id === selectedElementId)
  const selectedAssociation = relationships.associations.find((relationship) => relationship.id === selectedElementId)
  const selectedGeneralization = relationships.generalizations.find((relationship) => relationship.id === selectedElementId)
  const selectedRealization = relationships.realizations.find((relationship) => relationship.id === selectedElementId)
  const selectedClassifier = selected && 'kind' in selected ? selected : undefined
  const selectedOperation = selected && selected.elementType === 'OPERATION' ? selected as UmlOperationView : undefined
  const selectedProperty = selected && selected.elementType === 'PROPERTY' ? selected as UmlProperty : undefined
  const selectedParameter = selected && selected.elementType === 'PARAMETER' ? selected as UmlParameter : undefined
  const selectedParameterOperation = selectedParameter
    ? classifiers.flatMap((classifier) => classifier.operations).find((operation) => operation.id === selectedParameter.operationId)
    : undefined
  const selectedDiagramNode = selectedClassifier
    ? diagram.nodes.find((node) => node.umlElementId === selectedClassifier.id)
    : undefined
  const selectedRelationshipId = selectedAssociation?.id ?? selectedGeneralization?.id ?? selectedRealization?.id
  const selectedDiagramEdge = selectedRelationshipId
    ? diagram.edges.find((edge) => edge.umlElementId === selectedRelationshipId)
    : undefined

  return (
    <aside className="workspace-panel h-full min-h-0 overflow-auto border-l border-[var(--border)] bg-[var(--surface)]" aria-label="Panel de propiedades" onFocusCapture={() => canEdit && setEditing(true)} onBlurCapture={() => setEditing(false)}>
      <div className="panel-heading"><SlidersHorizontal size={16} /> Propiedades</div>
      {canEdit && selectedDiagramEdge && <RemoveFromDiagramButton diagramId={diagram.id} representationId={selectedDiagramEdge.id} kind="edge" onChanged={onDiagramChange} />}
      {selectedAssociation && <AssociationProperties key={selectedAssociation.id} modelId={modelId} association={selectedAssociation} classifiers={classifiers} canEdit={canEdit} onChanged={onSemanticChange} />}
      {selectedGeneralization && <DirectedRelationshipProperties key={selectedGeneralization.id} modelId={modelId} value={{ kind: 'GENERALIZATION', relationship: selectedGeneralization }} classifiers={classifiers} canEdit={canEdit} onChanged={onSemanticChange} />}
      {selectedRealization && <DirectedRelationshipProperties key={selectedRealization.id} modelId={modelId} value={{ kind: 'REALIZATION', relationship: selectedRealization }} classifiers={classifiers} canEdit={canEdit} onChanged={onSemanticChange} />}
      {!selected && !selectedAssociation && !selectedGeneralization && !selectedRealization ? <p className="p-5 text-sm leading-6 text-slate-400">Selecciona una clase, interfaz, atributo, operación o relación.</p> : selected ? <>
        <ElementSummary selected={selected} />
        {canEdit && selectedDiagramNode && <RemoveFromDiagramButton diagramId={diagram.id} representationId={selectedDiagramNode.id} kind="node" onChanged={onDiagramChange} />}
        {canEdit && selectedClassifier && <div className="border-t border-slate-200 p-4">
          <ClassifierActions modelId={modelId} classifier={selectedClassifier} classifiers={classifiers} onChanged={onSemanticChange} />
          <AttributeForm modelId={modelId} classifier={selectedClassifier} classifiers={classifiers} onCreated={onSemanticChange} />
          <OperationForm modelId={modelId} classifier={selectedClassifier} classifiers={classifiers} onCreated={onSemanticChange} />
        </div>}
        {canEdit && selectedOperation && <div className="border-t border-slate-200 p-4">
          <OperationForm modelId={modelId} classifier={classifiers.find((item) => item.id === selectedOperation.ownerClassifierId)!} classifiers={classifiers} operation={selectedOperation} onCreated={onSemanticChange} />
          <ParameterForm modelId={modelId} operation={selectedOperation} classifiers={classifiers} onCreated={onSemanticChange} />
        </div>}
        {canEdit && selectedProperty && <div className="border-t border-slate-200 p-4"><AttributeForm modelId={modelId} classifier={classifiers.find((item) => item.id === selectedProperty.ownerClassifierId)!} classifiers={classifiers} property={selectedProperty} onCreated={onSemanticChange} /></div>}
        {canEdit && selectedParameter && selectedParameterOperation && <div className="border-t border-slate-200 p-4"><ParameterForm modelId={modelId} operation={selectedParameterOperation} classifiers={classifiers} parameter={selectedParameter} onCreated={onSemanticChange} /></div>}
      </> : null}
    </aside>
  )
}

function RemoveFromDiagramButton({ diagramId, representationId, kind, onChanged }: {
  diagramId: string
  representationId: string
  kind: 'node' | 'edge'
  onChanged: () => Promise<unknown>
}) {
  const selectElement = useEditorStore((state) => state.selectElement)
  const remove = useMutation({
    mutationFn: () => kind === 'node'
      ? diagramApi.removeNode(diagramId, representationId)
      : diagramApi.removeEdge(diagramId, representationId),
    onSuccess: async () => {
      selectElement(null)
      await onChanged()
    },
  })
  return <div className="border-b border-slate-200 px-4 py-3">
    <button className="toolbar-button w-full justify-center" disabled={remove.isPending} onClick={() => remove.mutate()} type="button">Quitar del diagrama</button>
    {remove.error && <p className="form-error mt-2">{getApiErrorMessage(remove.error)}</p>}
  </div>
}

function ElementSummary({ selected }: { selected: SelectedElement }) {
  return <dl className="grid gap-3 p-4 text-sm">
    <PropertyValue label="Nombre" value={selected.name ?? 'Sin nombre'} />
    <PropertyValue label="Tipo" value={'kind' in selected ? selected.kind : selected.elementType} />
    {'visibility' in selected && <PropertyValue label="Visibilidad" value={selected.visibility} />}
    {'documentation' in selected && <PropertyValue label="Documentación" value={selected.documentation || 'Sin documentación'} />}
    {'properties' in selected && <PropertyValue label="Atributos" value={String(selected.properties.length)} />}
    {'operations' in selected && <PropertyValue label="Operaciones" value={String(selected.operations.length)} />}
  </dl>
}

function ClassifierActions({ modelId, classifier, classifiers, onChanged }: { modelId: string; classifier: UmlClassifierView; classifiers: UmlClassifierView[]; onChanged: () => Promise<unknown> }) {
  const selectElement = useEditorStore((state) => state.selectElement)
  const externalTypeUsages = classifiers
    .filter((owner) => owner.id !== classifier.id)
    .flatMap((owner) => [
      ...owner.properties
        .filter((property) => property.typeClassifierId === classifier.id)
        .map((property) => `${owner.name}.${property.name}`),
      ...owner.operations.flatMap((operation) => operation.parameters
        .filter((parameter) => parameter.typeClassifierId === classifier.id)
        .map((parameter) => `${owner.name}.${operation.name}(${parameter.direction === 'RETURN' ? 'retorno' : parameter.name || 'parámetro'})`)),
    ])
  const update = useMutation({ mutationFn: async () => {
    const name = window.prompt('Nombre de la Clase / Interfaz:', classifier.name)?.trim()
    if (!name) throw new Error('Edición cancelada.')
    return classifierApi.update(modelId, classifier.id, { name, documentation: classifier.documentation, packageId: classifier.packageId, kind: classifier.kind, visibility: classifier.visibility, isAbstract: classifier.isAbstract, isActive: classifier.isActive })
  }, onSuccess: onChanged })
  const remove = useMutation({ mutationFn: () => classifierApi.remove(modelId, classifier.id), onSuccess: async () => { selectElement(null); await onChanged() } })
  const requestDelete = () => {
    if (externalTypeUsages.length > 0) {
      window.alert(`No se puede eliminar ${classifier.name} porque se utiliza como tipo en:\n\n${externalTypeUsages.map((usage) => `• ${usage}`).join('\n')}\n\nCambia o elimina primero esas referencias.`)
      return
    }
    if (window.confirm(`¿Eliminar ${classifier.name} y sus elementos?`)) remove.mutate()
  }
  return <div className="mb-5 grid grid-cols-2 gap-2"><button className="toolbar-button justify-center" onClick={() => update.mutate()} type="button">Renombrar</button><button className="danger-button" onClick={requestDelete} type="button">Eliminar</button>{(update.error || remove.error) && <p className="form-error col-span-2">{getApiErrorMessage(update.error || remove.error)}</p>}</div>
}

const typedFeatureSchema = z.object({
  name: z.string().trim().min(1, 'Obligatorio').max(180),
  typeKind: z.enum(['PRIMITIVE', 'CLASSIFIER']),
  primitiveType: z.string(),
  typeClassifierId: z.string(),
  lowerBound: z.string().regex(/^\d+$/, 'Debe ser un entero positivo.'),
  upperBound: z.string().refine((value) => value.trim() === '*' || /^\d+$/.test(value.trim()), 'Usa un entero o *.'),
}).refine((value) => value.typeKind === 'PRIMITIVE' ? Boolean(value.primitiveType) : Boolean(value.typeClassifierId), { message: 'Selecciona un tipo.', path: ['primitiveType'] })

const attributeSchema = typedFeatureSchema.safeExtend({
  visibility: z.enum(visibilities),
  required: z.enum(['YES', 'NO']),
  isStatic: z.boolean(),
  isReadOnly: z.boolean(),
  isDerived: z.boolean(),
})
type AttributeValues = z.infer<typeof attributeSchema>

function AttributeForm({ modelId, classifier, classifiers, property, onCreated }: { modelId: string; classifier: UmlClassifierView; classifiers: UmlClassifierView[]; property?: UmlProperty; onCreated: () => Promise<unknown> }) {
  const isSimplePrimitiveMultiplicity = !property
    || (property.lowerBound === 1 && property.upperBound === 1)
    || (property.lowerBound === 0 && property.upperBound === 1)
  const [showAdvancedMultiplicity, setShowAdvancedMultiplicity] = useState(
    property?.typeKind === 'PRIMITIVE' && !isSimplePrimitiveMultiplicity,
  )
  const form = useForm<AttributeValues>({
    resolver: zodResolver(attributeSchema),
    defaultValues: {
      name: property?.name ?? '',
      visibility: property?.visibility ?? 'PRIVATE',
      typeKind: property?.typeKind ?? 'PRIMITIVE',
      primitiveType: property?.primitiveType ?? 'STRING',
      typeClassifierId: property?.typeClassifierId ?? '',
      required: property?.lowerBound === 0 && property?.upperBound === 1 ? 'NO' : 'YES',
      lowerBound: String(property?.lowerBound ?? 1),
      upperBound: property ? (property.upperBound == null ? '*' : String(property.upperBound)) : '1',
      isStatic: property?.isStatic ?? false,
      isReadOnly: property?.isReadOnly ?? false,
      isDerived: property?.isDerived ?? false,
    },
  })
  const typeKind = useWatch({ control: form.control, name: 'typeKind' })
  const mutation = useMutation({
    mutationFn: (values: AttributeValues) => {
      const useSimplePrimitiveMultiplicity = values.typeKind === 'PRIMITIVE' && !showAdvancedMultiplicity
      const input = {
        name: values.name,
        visibility: values.visibility,
        typeKind: values.typeKind,
        primitiveType: values.typeKind === 'PRIMITIVE' ? values.primitiveType as UmlPrimitiveType : null,
        typeClassifierId: values.typeKind === 'CLASSIFIER' ? values.typeClassifierId : null,
        lowerBound: useSimplePrimitiveMultiplicity ? (values.required === 'YES' ? 1 : 0) : Number(values.lowerBound),
        upperBound: useSimplePrimitiveMultiplicity ? 1 : parseUpperBound(values.upperBound),
        isStatic: values.isStatic,
        isReadOnly: values.isReadOnly,
        isDerived: values.isDerived,
      }
      return property ? featureApi.updateProperty(modelId, classifier.id, property.id, input) : featureApi.createProperty(modelId, classifier.id, input)
    },
    onSuccess: async () => {
      if (!property) {
        form.reset()
        setShowAdvancedMultiplicity(false)
      }
      await onCreated()
    },
  })
  const remove = useMutation({ mutationFn: () => featureApi.deleteProperty(modelId, classifier.id, property!.id), onSuccess: onCreated })
  return <EditorForm title={property ? 'Editar atributo' : 'Nuevo atributo'} submitLabel={property ? 'Guardar' : 'Crear'} onSubmit={form.handleSubmit((values) => mutation.mutate(values))} pending={mutation.isPending} error={mutation.error || remove.error} onDelete={property ? () => window.confirm(`¿Eliminar ${property.name}?`) && remove.mutate() : undefined}>
    <input className="editor-input" placeholder="name" {...form.register('name')} />
    <Select label="Visibilidad" registration={form.register('visibility')} options={visibilities} />
    <Select label="Tipo" registration={form.register('typeKind')} options={typeKindOptions} />
    {typeKind === 'PRIMITIVE' ? <Select label="Primitivo" registration={form.register('primitiveType')} options={primitiveOptions} /> : <Select label="Clase / Interfaz" registration={form.register('typeClassifierId')} options={classifiers.map((item) => ({ value: item.id, label: item.name }))} />}
    {typeKind === 'PRIMITIVE' ? <>
      <Select label="Obligatorio" registration={form.register('required')} options={[{ value: 'YES', label: 'Sí' }, { value: 'NO', label: 'No' }]} />
      <button className="text-left text-xs font-semibold text-indigo-600 hover:text-indigo-800" onClick={() => setShowAdvancedMultiplicity((visible) => !visible)} type="button">
        {showAdvancedMultiplicity ? 'Ocultar multiplicidad avanzada' : 'Editar multiplicidad avanzada'}
      </button>
      {showAdvancedMultiplicity && <MultiplicityFields register={form.register} />}
    </> : <MultiplicityFields register={form.register} />}
    <Check label="static" registration={form.register('isStatic')} /><Check label="readOnly" registration={form.register('isReadOnly')} /><Check label="derived" registration={form.register('isDerived')} />
  </EditorForm>
}

const operationSchema = z.object({
  name: z.string().trim().min(1).max(180),
  visibility: z.enum(visibilities),
  isAbstract: z.boolean(),
  isStatic: z.boolean(),
  isQuery: z.boolean(),
  returnKind: z.enum(['VOID', 'PRIMITIVE', 'CLASSIFIER']),
  returnPrimitiveType: z.string(),
  returnClassifierId: z.string(),
}).refine((value) => value.returnKind !== 'CLASSIFIER' || Boolean(value.returnClassifierId), {
  message: 'Selecciona una clase o interfaz para el retorno.',
  path: ['returnClassifierId'],
})
type OperationValues = z.infer<typeof operationSchema>

function OperationForm({ modelId, classifier, classifiers, operation, onCreated }: { modelId: string; classifier: UmlClassifierView; classifiers: UmlClassifierView[]; operation?: UmlOperationView; onCreated: () => Promise<unknown> }) {
  const currentReturn = operation?.parameters.find((parameter) => parameter.direction === 'RETURN')
  const nextPosition = operation?.parameters.reduce((maximum, parameter) => Math.max(maximum, parameter.position), -1) ?? -1
  const initialReturnKind = currentReturn?.typeKind ?? 'VOID'
  const form = useForm<OperationValues>({ resolver: zodResolver(operationSchema), defaultValues: { name: operation?.name ?? '', visibility: operation?.visibility ?? 'PUBLIC', isAbstract: operation?.isAbstract ?? false, isStatic: operation?.isStatic ?? false, isQuery: operation?.isQuery ?? false, returnKind: initialReturnKind, returnPrimitiveType: currentReturn?.primitiveType ?? 'BOOLEAN', returnClassifierId: currentReturn?.typeClassifierId ?? '' } })
  const returnKind = useWatch({ control: form.control, name: 'returnKind' })
  const mutation = useMutation({ mutationFn: async (values: OperationValues) => {
    const operationInput = { name: values.name, visibility: values.visibility, isAbstract: values.isAbstract, isStatic: values.isStatic, isQuery: values.isQuery }
    const saved = operation ? await featureApi.updateOperation(modelId, classifier.id, operation.id, operationInput) : await featureApi.createOperation(modelId, classifier.id, operationInput)
    const returnInput = values.returnKind === 'VOID' ? null : {
      name: null, direction: 'RETURN' as const, typeKind: values.returnKind as UmlTypeKind,
      primitiveType: values.returnKind === 'PRIMITIVE' ? values.returnPrimitiveType as UmlPrimitiveType : null,
      typeClassifierId: values.returnKind === 'CLASSIFIER' ? values.returnClassifierId : null,
      lowerBound: 1, upperBound: 1, position: currentReturn?.position ?? nextPosition + 1,
    }
    if (!returnInput && currentReturn) await featureApi.deleteParameter(modelId, saved.id, currentReturn.id)
    if (returnInput && currentReturn) await featureApi.updateParameter(modelId, saved.id, currentReturn.id, returnInput)
    if (returnInput && !currentReturn) await featureApi.createParameter(modelId, saved.id, returnInput)
    return saved
  }, onSuccess: async () => { if (!operation) form.reset(); await onCreated() } })
  const remove = useMutation({ mutationFn: () => featureApi.deleteOperation(modelId, classifier.id, operation!.id), onSuccess: onCreated })
  return <EditorForm title={operation ? 'Editar operación' : 'Nueva operación'} submitLabel={operation ? 'Guardar' : 'Crear'} onSubmit={form.handleSubmit((values) => mutation.mutate(values))} pending={mutation.isPending} error={mutation.error || remove.error} onDelete={operation ? () => window.confirm(`¿Eliminar ${operation.name} y sus parámetros?`) && remove.mutate() : undefined}>
    <input className="editor-input" placeholder="register" {...form.register('name')} />
    <Select label="Visibilidad" registration={form.register('visibility')} options={visibilities} />
    <Check label="abstract" registration={form.register('isAbstract')} /><Check label="static" registration={form.register('isStatic')} /><Check label="query" registration={form.register('isQuery')} />
    <Select label="Retorno" registration={form.register('returnKind')} options={[{ value: 'VOID', label: 'Sin retorno / void' }, ...typeKindOptions]} />
    {returnKind === 'PRIMITIVE' && <Select label="Tipo de retorno" registration={form.register('returnPrimitiveType')} options={primitiveOptions} />}
    {returnKind === 'CLASSIFIER' && <Select label="Clase / Interfaz de retorno" registration={form.register('returnClassifierId')} options={classifiers.map((item) => ({ value: item.id, label: item.name }))} />}
  </EditorForm>
}

const parameterSchema = typedFeatureSchema.safeExtend({ direction: z.enum(['IN', 'OUT', 'INOUT', 'RETURN']), position: z.string().regex(/^\d+$/, 'Debe ser un entero positivo.') })
type ParameterValues = z.infer<typeof parameterSchema>

function ParameterForm({ modelId, operation, classifiers, parameter, onCreated }: { modelId: string; operation: UmlOperationView; classifiers: UmlClassifierView[]; parameter?: UmlParameter; onCreated: () => Promise<unknown> }) {
  const nextPosition = operation.parameters.reduce((max, item) => Math.max(max, item.position), -1) + 1
  const form = useForm<ParameterValues>({ resolver: zodResolver(parameterSchema), defaultValues: { name: parameter?.name ?? 'value', direction: parameter?.direction ?? 'IN', typeKind: parameter?.typeKind ?? 'PRIMITIVE', primitiveType: parameter?.primitiveType ?? 'STRING', typeClassifierId: parameter?.typeClassifierId ?? '', lowerBound: String(parameter?.lowerBound ?? 1), upperBound: parameter?.upperBound == null ? '*' : String(parameter.upperBound), position: String(parameter?.position ?? nextPosition) } })
  const typeKind = useWatch({ control: form.control, name: 'typeKind' })
  const direction = useWatch({ control: form.control, name: 'direction' }) as UmlParameterDirection
  const mutation = useMutation({ mutationFn: (values: ParameterValues) => {
    const input = { name: values.direction === 'RETURN' ? null : values.name, direction: values.direction, typeKind: values.typeKind as UmlTypeKind, primitiveType: values.typeKind === 'PRIMITIVE' ? values.primitiveType as UmlPrimitiveType : null, typeClassifierId: values.typeKind === 'CLASSIFIER' ? values.typeClassifierId : null, lowerBound: Number(values.lowerBound), upperBound: parseUpperBound(values.upperBound), position: Number(values.position) }
    return parameter ? featureApi.updateParameter(modelId, operation.id, parameter.id, input) : featureApi.createParameter(modelId, operation.id, input)
  }, onSuccess: async () => { if (!parameter) form.reset({ ...form.getValues(), name: 'value', position: String(nextPosition + 1) }); await onCreated() } })
  const remove = useMutation({ mutationFn: () => featureApi.deleteParameter(modelId, operation.id, parameter!.id), onSuccess: onCreated })
  return <EditorForm title={parameter ? parameter.direction === 'RETURN' ? 'Editar retorno' : 'Editar parámetro' : 'Nuevo parámetro'} submitLabel={parameter ? 'Guardar' : 'Crear'} onSubmit={form.handleSubmit((values) => mutation.mutate(values))} pending={mutation.isPending} error={mutation.error || remove.error} onDelete={parameter ? () => window.confirm('¿Eliminar este parámetro?') && remove.mutate() : undefined}>
    {direction !== 'RETURN' && <input className="editor-input" placeholder="email" {...form.register('name')} />}
    <Select label="Dirección" registration={form.register('direction')} options={['IN', 'OUT', 'INOUT', 'RETURN']} />
    <Select label="Tipo" registration={form.register('typeKind')} options={typeKindOptions} />
    {typeKind === 'PRIMITIVE' ? <Select label="Primitivo" registration={form.register('primitiveType')} options={primitiveOptions} /> : <Select label="Clase / Interfaz" registration={form.register('typeClassifierId')} options={classifiers.map((item) => ({ value: item.id, label: item.name }))} />}
    <MultiplicityFields register={form.register} />
  </EditorForm>
}

function EditorForm({ title, children, onSubmit, pending, error, submitLabel = 'Crear', onDelete }: { title: string; children: React.ReactNode; onSubmit: React.FormEventHandler<HTMLFormElement>; pending: boolean; error: Error | null; submitLabel?: string; onDelete?: () => void | false }) {
  return <form className="mb-5 grid gap-2 rounded-xl bg-slate-50 p-3" onSubmit={onSubmit}><h3 className="mb-1 flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-slate-600"><Plus size={13} />{title}</h3>{children}{error && <p className="text-xs text-rose-600">{getApiErrorMessage(error)}</p>}<div className="mt-1 grid grid-cols-2 gap-2"><button className="primary-button !min-h-8 !py-1.5" disabled={pending} type="submit">{submitLabel}</button>{onDelete && <button className="danger-button" onClick={onDelete} type="button">Eliminar</button>}</div></form>
}

function Select({ label, registration, options }: { label: string; registration: object; options: (string | { value: string; label: string })[] }) {
  return <label className="editor-label">{label}<select className="editor-input" {...registration}>{options.map((option) => { const value = typeof option === 'string' ? option : option.value; const text = typeof option === 'string' ? option : option.label; return <option key={value} value={value}>{text}</option> })}</select></label>
}

function Check({ label, registration }: { label: string; registration: object }) {
  return <label className="flex items-center gap-2 text-xs text-slate-600"><input type="checkbox" {...registration} />{label}</label>
}

function MultiplicityFields({ register }: { register: (name: 'lowerBound' | 'upperBound') => object }) {
  return <div className="grid grid-cols-2 gap-2"><label className="editor-label">Límite inferior<input className="editor-input" min="0" type="number" {...register('lowerBound')} /></label><label className="editor-label">Límite superior<input className="editor-input" placeholder="*" {...register('upperBound')} /></label></div>
}

function parseUpperBound(value: string) {
  return value.trim() === '*' || value.trim() === '' ? null : Number(value)
}

function primitiveDisplayName(value: UmlPrimitiveType) {
  return ({ STRING: 'String', INTEGER: 'Integer', BOOLEAN: 'Boolean', REAL: 'Real', UNLIMITED_NATURAL: 'Unlimited Natural' })[value]
}

function PropertyValue({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return <div><dt className="mb-1 text-xs font-semibold uppercase tracking-wider text-slate-400">{label}</dt><dd className={`break-words text-slate-700 ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd></div>
}
