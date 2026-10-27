import { useCallback, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../../api/apiError'
import { PageState } from '../../../components/ui/PageState'
import { DismissibleAlert } from '../../../components/ui/DismissibleAlert'
import { useProjectPermission } from '../../auth/hooks/useProjectPermission'
import { useCurrentUser } from '../../auth/hooks/useCurrentUser'
import { projectApi } from '../../projects/api/projectApi'
import { useEditorStore } from '../store/editorStore'
import { useRealtimeDiagram } from '../hooks/useRealtimeDiagram'
import { classifierApi } from '../api/classifierApi'
import { diagramApi, type DiagramGeometryInput } from '../api/diagramApi'
import { relationshipApi } from '../api/relationshipApi'
import { modelApi } from '../api/modelApi'
import { loadSemanticModel } from '../api/semanticModelApi'
import { DiagramCanvas } from '../components/DiagramCanvas'
import { ModelExplorer } from '../components/ModelExplorer'
import { ModelValidationPanel } from '../components/ModelValidationPanel'
import { ModelingToolbar, type RelationshipTool } from '../components/ModelingToolbar'
import { PropertiesPanel } from '../components/PropertiesPanel'
import { DiagramToolbox } from '../components/DiagramToolbox'
import { ResizableSidePanel } from '../components/ResizableSidePanel'
import { allocateEdgeLayout, reallocateEdgeLayout } from '../mapper/diagramRouting'
import { useWorkspaceStore } from '../store/workspaceStore'
import { useAppearanceStore } from '../../appearance/appearanceStore'
import { aiSessionApi } from '../../ai-sessions/api/aiSessionApi'
import { canAccessAiSessions, currentSessionKey } from '../../ai-sessions/aiSessionUi'
import { AiCollaborativeDrawer } from '../../ai-chat/components/AiCollaborativeDrawer'
import type { ClassDiagram, DiagramNode, UmlAggregationKind, UmlClassifierKind, UmlClassifierView, UmlRelationships } from '../types/uml'

const NODE_WIDTH = 310
const NODE_HEIGHT = 180

export function ModelEditorPage() {
  const { projectId = '', modelId = '', diagramId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUser = useCurrentUser()
  const selectedElementId = useEditorStore((state) => state.selectedElementId)
  const presenceActivity = useEditorStore((state) => state.presenceActivity)
  const [fitViewRequest, setFitViewRequest] = useState(0)
  const [aiOpen, setAiOpen] = useState(false)
  const workspace = useWorkspaceStore()
  const openAppearance = useAppearanceStore((state) => state.openPanel)
  const permission = useProjectPermission(projectId)
  const project = useQuery({ queryKey: ['projects', projectId], queryFn: () => projectApi.get(projectId), enabled: Boolean(projectId) })
  const canAccessAi = canAccessAiSessions(permission.data)
  const currentAiSession = useQuery({ queryKey: currentSessionKey(projectId), queryFn: () => aiSessionApi.getCurrentSession(projectId), enabled: Boolean(projectId) && canAccessAi, retry: false })
  const selectDiagram = (selectedId: string) => {
    useEditorStore.getState().selectElement(null)
    navigate(`/projects/${projectId}/models/${modelId}/diagrams/${selectedId}`)
  }
  const semanticModel = useQuery({ queryKey: ['uml-models', modelId, 'semantic'], queryFn: () => loadSemanticModel(modelId), enabled: Boolean(modelId) })
  const diagrams = useQuery({ queryKey: ['uml-models', modelId, 'diagrams'], queryFn: () => diagramApi.list(modelId), enabled: Boolean(modelId) })

  const effectiveDiagramId = diagramId || null
  const diagram = useQuery({ queryKey: ['diagrams', effectiveDiagramId], queryFn: () => diagramApi.get(effectiveDiagramId!), enabled: Boolean(effectiveDiagramId) })
  const collaboration = useRealtimeDiagram({
    modelId,
    diagramId: effectiveDiagramId,
    currentUserId: currentUser.data?.id ?? null,
    selectedElementId,
    presenceActivity,
    classifiers: semanticModel.data?.classifiers ?? [],
    diagram: diagram.data,
  })
  const refreshDiagram = useCallback(async () => {
    if (effectiveDiagramId) await queryClient.invalidateQueries({ queryKey: ['diagrams', effectiveDiagramId] })
    await queryClient.invalidateQueries({ queryKey: ['uml-models', modelId, 'diagrams'] })
  }, [effectiveDiagramId, modelId, queryClient])
  const refreshSemantic = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ['uml-models', modelId, 'semantic'] })
    await queryClient.invalidateQueries({ queryKey: ['model-validation', modelId] })
    await refreshDiagram()
  }, [modelId, queryClient, refreshDiagram])

  const createDiagram = useMutation({
    mutationFn: async () => {
      const name = window.prompt('Nombre del diagrama de clases:', 'Diagrama principal')?.trim()
      if (!name) throw new Error('Creación cancelada.')
      return diagramApi.create(modelId, name)
    },
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ['uml-models', modelId, 'diagrams'] })
      selectDiagram(created.id)
    },
  })
  const createClassifier = useMutation({
    mutationFn: async (kind: UmlClassifierKind) => {
      if (!diagram.data) throw new Error('Primero crea o selecciona un diagrama.')
      const suggestedName = kind === 'CLASS' ? 'NewClass' : 'NewInterface'
      const name = window.prompt(`Nombre de ${kind === 'CLASS' ? 'la clase' : 'la interfaz'}:`, suggestedName)?.trim()
      if (!name) throw new Error('Creación cancelada.')
      const classifier = await classifierApi.create(modelId, { name, kind, visibility: 'PUBLIC', isAbstract: false, isActive: false })
      await diagramApi.addNode(diagram.data.id, classifier.id, nextGeometry(diagram.data.nodes.length))
      return classifier
    },
    onSuccess: refreshSemantic,
  })
  const addExisting = useMutation({
    mutationFn: async () => {
      if (!diagram.data || !semanticModel.data) throw new Error('No hay un diagrama disponible.')
      const represented = new Set(diagram.data.nodes.map((node) => node.umlElementId))
      const available = semanticModel.data.classifiers.filter((item) => !represented.has(item.id))
      const selected = chooseClassifier('Elemento semántico para añadir al diagrama', available)
      await diagramApi.addNode(diagram.data.id, selected.id, nextGeometry(diagram.data.nodes.length))
    },
    onSuccess: refreshDiagram,
  })
  const syncRelationships = useMutation({
    mutationFn: async () => {
      if (diagram.data && semanticModel.data) await addMissingRelationshipEdges(diagram.data, semanticModel.data.relationships)
    },
    onSuccess: refreshDiagram,
  })
  const createRelationship = useMutation({
    mutationFn: async (tool: RelationshipTool) => {
      if (!diagram.data || !semanticModel.data) throw new Error('No hay un diagrama disponible.')
      const visibleIds = new Set(diagram.data.nodes.map((node) => node.umlElementId))
      const visibleClassifiers = semanticModel.data.classifiers.filter((item) => visibleIds.has(item.id))
      const created = await createSemanticRelationship(modelId, tool, visibleClassifiers)
      const [sourceElementId, targetElementId] = relationshipEndpoints(created)
      const source = diagram.data.nodes.find((node) => node.umlElementId === sourceElementId)
      const target = diagram.data.nodes.find((node) => node.umlElementId === targetElementId)
      if (!source || !target) throw new Error('Los extremos deben estar en el diagrama actual.')
      await diagramApi.addEdge(diagram.data.id, created.id, source.id, target.id, allocateEdgeLayout(diagram.data, source, target))
    },
    onSuccess: refreshSemantic,
  })
  const updateGeometry = useMutation({
    mutationFn: async ({ node, geometry }: { node: DiagramNode; geometry: DiagramGeometryInput }) => {
      const currentDiagram = diagram.data!
      const updatedNode = await diagramApi.updateNodeGeometry(currentDiagram.id, node.id, geometry)
      const nodes = currentDiagram.nodes.map((item) => item.id === node.id ? updatedNode : item)
      let workingDiagram = { ...currentDiagram, nodes }
      const incidentEdges = workingDiagram.edges.filter((item) => item.sourceNodeId === node.id || item.targetNodeId === node.id)
      for (const edge of incidentEdges) {
        const layout = reallocateEdgeLayout(workingDiagram, edge, nodes)
        if (sameLayout(edge, layout)) continue
        const updatedEdge = await diagramApi.updateEdgeLayout(workingDiagram.id, edge.id, layout)
        workingDiagram = {
          ...workingDiagram,
          edges: workingDiagram.edges.map((item) => item.id === edge.id ? updatedEdge : item),
        }
      }
      return updatedNode
    },
    onSuccess: refreshDiagram,
  })
  const exportXmi = useMutation({
    mutationFn: async () => {
      const modelName = semanticModel.data?.model.name ?? 'uml-model'
      return modelApi.exportXmi(modelId, modelName)
    },
    onSuccess: ({ content, fileName }) => {
      const url = URL.createObjectURL(content)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = fileName
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    },
  })
  const onGeometryChange = useCallback((node: DiagramNode, geometry: DiagramGeometryInput) => updateGeometry.mutate({ node, geometry }), [updateGeometry])
  const operationError = useMemo(() => [createDiagram.error, createClassifier.error, addExisting.error, syncRelationships.error, createRelationship.error, updateGeometry.error, exportXmi.error].find(Boolean), [addExisting.error, createClassifier.error, createDiagram.error, createRelationship.error, exportXmi.error, syncRelationships.error, updateGeometry.error])

  if (semanticModel.isPending || diagrams.isPending || project.isPending) return <PageState title="Cargando modelo…" />
  if (project.isError) return <PageState title="No se pudo cargar el proyecto" detail={getApiErrorMessage(project.error)} />
  if (semanticModel.isError) return <PageState title="No se pudo cargar el modelo" detail={getApiErrorMessage(semanticModel.error)} />
  if (diagrams.isError) return <PageState title="No se pudieron cargar los diagramas" detail={getApiErrorMessage(diagrams.error)} />

  if (!diagramId && diagrams.data.length > 0) {
    return <Navigate replace to={`/projects/${projectId}/models/${modelId}/diagrams/${diagrams.data[0].id}`} />
  }

  const canEdit = permission.data?.canEdit ?? false
  const { model, classifiers, relationships } = semanticModel.data
  if (model.projectId !== projectId) return <PageState title="El modelo no pertenece a este proyecto" />
  if (!effectiveDiagramId && diagrams.data.length === 0) {
    return <main className="app-themed grid min-h-screen place-items-center bg-[var(--app-bg)] p-6"><section className="max-w-md rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8 text-center shadow-sm">
      <h1 className="text-xl font-bold text-slate-900">Este modelo todavía no tiene diagramas</h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">Crea un diagrama de clases para elegir qué elementos semánticos visualizar y conservar su posición.</p>
      {canEdit ? <button className="primary-button mt-5" disabled={createDiagram.isPending} onClick={() => createDiagram.mutate()} type="button">Crear diagrama de clases</button> : <p className="mt-5 text-sm font-semibold text-amber-700">Necesitas permiso de edición para crear el primer diagrama.</p>}
      <button className="toolbar-button mt-3" disabled={exportXmi.isPending} onClick={() => exportXmi.mutate()} type="button">{exportXmi.isPending ? 'Exportando…' : 'Exportar XMI'}</button>
      <div className="mt-3 flex justify-center gap-2"><Link className="toolbar-button" to="/help">Ayuda</Link><button className="toolbar-button" onClick={openAppearance} type="button">Apariencia</button></div>
      {createDiagram.error && <p className="form-error mt-3">{getApiErrorMessage(createDiagram.error)}</p>}
      {exportXmi.error && <p className="form-error mt-3">{getApiErrorMessage(exportXmi.error)}</p>}
    </section></main>
  }
  if (diagram.isError) return <PageState title="No se pudo cargar el diagrama" detail={getApiErrorMessage(diagram.error)} />
  if (diagram.isPending || !diagram.data) return <PageState title="Cargando diagrama…" />
  if (diagram.data.modelId !== modelId) return <PageState title="El diagrama no pertenece a este modelo" detail="Selecciona un diagrama desde la lista del modelo correcto." />

  const busy = createDiagram.isPending || createClassifier.isPending || addExisting.isPending || syncRelationships.isPending || createRelationship.isPending
  const gridColumns = [workspace.leftMode !== 'HIDDEN' ? workspace.leftMode === 'COLLAPSED' ? '44px' : `${workspace.leftWidth}px` : null,
    'minmax(0, 1fr)', workspace.toolboxVisible ? '240px' : null,
    workspace.rightMode !== 'HIDDEN' ? workspace.rightMode === 'COLLAPSED' ? '44px' : `${workspace.rightWidth}px` : null].filter(Boolean).join(' ')
  const focusMode = workspace.restoreSnapshot !== null
  return <main className="app-themed workspace-layout workspace-surface relative grid h-screen grid-rows-[auto_minmax(0,1fr)] overflow-hidden" style={{ gridTemplateColumns: gridColumns }}>
    <ModelingToolbar projectId={projectId} projectName={project.data.name} modelName={model.name} semanticRevision={model.revision} diagram={diagram.data} canEdit={canEdit} isCreating={busy} onAddExisting={() => addExisting.mutate()} onSyncRelationships={() => syncRelationships.mutate()} onCreateClassifier={(kind) => createClassifier.mutate(kind)} onCreateRelationship={(tool) => createRelationship.mutate(tool)} onToggleToolbox={() => workspace.setToolboxVisible(!workspace.toolboxVisible)} toolboxVisible={workspace.toolboxVisible} leftPanelHidden={workspace.leftMode === 'HIDDEN'} rightPanelHidden={workspace.rightMode === 'HIDDEN'} onShowLeftPanel={() => workspace.setLeftMode('VISIBLE')} onShowRightPanel={() => workspace.setRightMode('VISIBLE')} onFocusDiagram={workspace.focusDiagram} onRestorePanels={workspace.restorePanels} focusMode={focusMode} onRefresh={() => { void semanticModel.refetch(); void diagram.refetch(); void diagrams.refetch() }} onFitView={() => setFitViewRequest((value) => value + 1)} onExportXmi={() => exportXmi.mutate()} isExportingXmi={exportXmi.isPending} showAi={canAccessAi} aiOpen={aiOpen} onToggleAi={() => setAiOpen((value) => !value)} />
    {workspace.leftMode !== 'HIDDEN' && <ResizableSidePanel side="LEFT" label="Diagramas" mode={workspace.leftMode} width={workspace.leftWidth} onResize={workspace.setLeftWidth} onModeChange={workspace.setLeftMode}><ModelExplorer classifiers={classifiers} packages={semanticModel.data.packages} diagrams={diagrams.data} activeDiagramId={diagram.data.id} canEdit={canEdit} isCreating={createDiagram.isPending} onSelectDiagram={selectDiagram} onCreateDiagram={() => createDiagram.mutate()} validationPanel={<ModelValidationPanel modelId={modelId} revision={model.revision} canEdit={canEdit} onLocate={(id) => {
      const association = relationships.associations.find((item) => item.ends.some((end) => end.propertyId === id))
      useEditorStore.getState().selectElement(association?.id ?? id)
    }} />} /></ResizableSidePanel>}
    <DiagramCanvas key={diagram.data.id} classifiers={classifiers} relationships={relationships} diagram={diagram.data} canEdit={canEdit} fitViewRequest={fitViewRequest} onGeometryChange={onGeometryChange} participants={collaboration.participants} activity={collaboration.activity} highlights={collaboration.highlights} currentUserId={currentUser.data?.id ?? null} />
    {workspace.toolboxVisible && <DiagramToolbox disabled={busy} onClose={() => workspace.setToolboxVisible(false)} onCreateClassifier={(kind) => createClassifier.mutate(kind)} onCreateRelationship={(tool) => createRelationship.mutate(tool)} />}
    {workspace.rightMode !== 'HIDDEN' && <ResizableSidePanel side="RIGHT" label="Propiedades" mode={workspace.rightMode} width={workspace.rightWidth} onResize={workspace.setRightWidth} onModeChange={workspace.setRightMode}><PropertiesPanel modelId={modelId} diagram={diagram.data} classifiers={classifiers} relationships={relationships} canEdit={canEdit} onSemanticChange={refreshSemantic} onDiagramChange={refreshDiagram} /></ResizableSidePanel>}
    {canAccessAi && <AiCollaborativeDrawer open={aiOpen} onClose={() => setAiOpen(false)} projectId={projectId} session={currentAiSession.data} modelId={modelId} modelName={model.name} diagramId={diagram.data.id} diagramName={diagram.data.name} />}
    {operationError && operationError.message !== 'Creación cancelada.' && <div className="absolute bottom-5 left-1/2 z-50 w-[min(92vw,560px)] -translate-x-1/2"><DismissibleAlert key={`${operationError.message}-${Math.max(createDiagram.submittedAt, createClassifier.submittedAt, addExisting.submittedAt, syncRelationships.submittedAt, createRelationship.submittedAt, updateGeometry.submittedAt, exportXmi.submittedAt)}`} variant="ERROR">{friendlyOperationError(operationError)}</DismissibleAlert></div>}
  </main>
}

async function addMissingRelationshipEdges(diagram: ClassDiagram, relationships: UmlRelationships) {
  let workingDiagram = diagram
  const representedEdges = new Set(workingDiagram.edges.map((edge) => edge.umlElementId))
  const nodeByElement = new Map(workingDiagram.nodes.map((node) => [node.umlElementId, node]))
  const all = [...relationships.associations, ...relationships.generalizations, ...relationships.realizations]
  for (const relationship of all) {
    if (representedEdges.has(relationship.id)) continue
    const [sourceId, targetId] = relationshipEndpoints(relationship)
    const source = nodeByElement.get(sourceId)
    const target = nodeByElement.get(targetId)
    if (source && target) {
      const created = await diagramApi.addEdge(workingDiagram.id, relationship.id, source.id, target.id, allocateEdgeLayout(workingDiagram, source, target))
      workingDiagram = { ...workingDiagram, edges: [...workingDiagram.edges, created] }
    }
  }
}

function relationshipEndpoints(relationship: UmlRelationships['associations'][number] | UmlRelationships['generalizations'][number] | UmlRelationships['realizations'][number]): [string, string] {
  if ('ends' in relationship) return [relationship.ends[0].classifierId, relationship.ends[1].classifierId]
  if ('specificClassifierId' in relationship) return [relationship.specificClassifierId, relationship.generalClassifierId]
  return [relationship.clientClassifierId, relationship.supplierClassifierId]
}

async function createSemanticRelationship(modelId: string, tool: RelationshipTool, classifiers: UmlClassifierView[]) {
  if (classifiers.length === 0) throw new Error('Añade primero al menos una clase o interfaz al diagrama.')
  if (tool === 'GENERALIZATION') {
    const specific = chooseClassifier('Clase / Interfaz específica (hija)', classifiers)
    const general = chooseClassifier('Clase / Interfaz general (padre)', classifiers.filter((item) => item.kind === specific.kind))
    return relationshipApi.createGeneralization(modelId, specific.id, general.id)
  }
  if (tool === 'REALIZATION') {
    const client = chooseClassifier('Clase cliente', classifiers.filter((item) => item.kind === 'CLASS'))
    const supplier = chooseClassifier('Interfaz proveedora', classifiers.filter((item) => item.kind === 'INTERFACE'))
    return relationshipApi.createRealization(modelId, client.id, supplier.id)
  }
  const first = chooseClassifier('Primer extremo (whole para agregación/composición)', classifiers)
  const second = chooseClassifier('Segundo extremo', classifiers)
  const aggregation: UmlAggregationKind = tool === 'AGGREGATION' ? 'SHARED' : tool === 'COMPOSITION' ? 'COMPOSITE' : 'NONE'
  return relationshipApi.createAssociation(modelId, { name: null, firstEnd: { classifierId: first.id, lowerBound: 1, upperBound: 1, navigable: false, aggregation }, secondEnd: { classifierId: second.id, lowerBound: 0, upperBound: null, navigable: false, aggregation: 'NONE' } })
}

function chooseClassifier(label: string, classifiers: UmlClassifierView[]) {
  if (classifiers.length === 0 && label === 'Elemento semántico para añadir al diagrama') throw new Error('No hay elementos semánticos disponibles para añadir al diagrama.')
  if (classifiers.length === 0) throw new Error(`No hay opciones disponibles para: ${label}.`)
  const options = classifiers.map((classifier, index) => `${index + 1}. ${classifier.name}`).join('\n')
  const answer = window.prompt(`${label}:\n${options}`, '1')
  if (answer === null) throw new Error('Creación cancelada.')
  const selected = classifiers[Number(answer) - 1]
  if (!selected) throw new Error('Selecciona un número válido.')
  return selected
}

function friendlyOperationError(error: Error) {
  return getApiErrorMessage(error)
}

function nextGeometry(index: number): DiagramGeometryInput {
  return { x: 50 + (index % 3) * 380, y: 50 + Math.floor(index / 3) * 250, width: NODE_WIDTH, height: NODE_HEIGHT, zIndex: index }
}

function sameLayout(edge: ClassDiagram['edges'][number], layout: ReturnType<typeof reallocateEdgeLayout>) {
  return edge.sourceSide === layout.sourceSide
    && edge.targetSide === layout.targetSide
    && edge.sourceAnchorIndex === layout.sourceAnchorIndex
    && edge.targetAnchorIndex === layout.targetAnchorIndex
    && edge.waypoints.length === 0
}
