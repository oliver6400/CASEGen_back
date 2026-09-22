import { Background, BackgroundVariant, Controls, MiniMap, ReactFlow, useNodesState } from '@xyflow/react'
import type { Edge, Node, ReactFlowInstance } from '@xyflow/react'
import { useEffect, useMemo, useRef } from 'react'
import { umlToFlow } from '../mapper/umlToFlow'
import type { UmlNodeData } from '../mapper/umlToFlow'
import { UmlClassNode } from '../nodes/UmlClassNode'
import { UmlInterfaceNode } from '../nodes/UmlInterfaceNode'
import { AssociationEdge } from '../edges/AssociationEdge'
import { GeneralizationEdge } from '../edges/GeneralizationEdge'
import { RealizationEdge } from '../edges/RealizationEdge'
import { useEditorStore } from '../store/editorStore'
import type { ClassDiagram, DiagramNode, UmlClassifierView, UmlRelationships } from '../types/uml'
import type { CollaborationActivity, PresenceParticipant } from '../api/collaborationApi'
import type { CollaborationHighlight } from '../hooks/useRealtimeDiagram'
import { CollaborationPanel } from './CollaborationPanel'

const nodeTypes = { umlClass: UmlClassNode, umlInterface: UmlInterfaceNode }
const edgeTypes = { association: AssociationEdge, generalization: GeneralizationEdge, realization: RealizationEdge }

export function DiagramCanvas({
  classifiers, relationships, diagram, canEdit, fitViewRequest, onGeometryChange,
  participants, activity, highlights, currentUserId,
}: {
  classifiers: UmlClassifierView[]
  relationships: UmlRelationships
  diagram: ClassDiagram
  canEdit: boolean
  fitViewRequest: number
  onGeometryChange: (node: DiagramNode, geometry: { x: number; y: number; width: number; height: number }) => void
  participants: PresenceParticipant[]
  activity: CollaborationActivity[]
  highlights: CollaborationHighlight[]
  currentUserId: string | null
}) {
  const selectedElementId = useEditorStore((state) => state.selectedElementId)
  const selectElement = useEditorStore((state) => state.selectElement)
  const projection = useMemo(
    () => umlToFlow(classifiers, relationships, diagram, selectedElementId, canEdit, onGeometryChange, participants, currentUserId, highlights),
    [canEdit, classifiers, currentUserId, diagram, highlights, onGeometryChange, participants, relationships, selectedElementId],
  )
  const [nodes, setNodes, onNodesChange] = useNodesState(projection.nodes)
  const edges = useMemo<Edge[]>(() => projection.edges, [projection.edges])
  const flowInstance = useRef<ReactFlowInstance<(typeof projection.nodes)[number], Edge> | null>(null)
  const previousFitRequest = useRef(fitViewRequest)

  useEffect(() => setNodes(projection.nodes), [projection.nodes, setNodes])

  useEffect(() => {
    if (fitViewRequest !== previousFitRequest.current) {
      previousFitRequest.current = fitViewRequest
      void flowInstance.current?.fitView({ padding: 0.2, duration: 250 })
    }
  }, [fitViewRequest])

  return (
    <section className="workspace-canvas relative min-h-0 overflow-hidden bg-[var(--surface-secondary)]" aria-label="Diagrama UML">
      <CollaborationPanel participants={participants} activity={activity} />
      {diagram.nodes.length === 0 && <div className="pointer-events-none absolute inset-0 z-10 grid place-items-center text-sm text-slate-500">Este diagrama aún no contiene clases ni interfaces.</div>}
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onInit={(instance) => { flowInstance.current = instance }}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        nodesConnectable={false}
        nodesDraggable={canEdit}
        onNodeClick={(_, node) => selectElement((node.data as UmlNodeData).classifier.id)}
        onNodeDragStop={(_, node) => commitDraggedGeometry(node, onGeometryChange)}
        onEdgeClick={(_, edge) => selectElement(edge.id)}
        onPaneClick={() => selectElement(null)}
        fitView
        fitViewOptions={{ padding: 0.2 }}
      >
        <Background variant={BackgroundVariant.Dots} gap={18} size={1} color="#cbd5e1" />
        <Controls />
        <MiniMap pannable zoomable nodeColor={(node) => node.type === 'umlInterface' ? '#0f766e' : '#4f46e5'} />
      </ReactFlow>
    </section>
  )
}

function commitDraggedGeometry(
  node: Node<UmlNodeData>,
  onGeometryChange: (node: DiagramNode, geometry: { x: number; y: number; width: number; height: number }) => void,
) {
  onGeometryChange(node.data.diagramNode, {
    x: node.position.x,
    y: node.position.y,
    width: node.measured?.width ?? node.width ?? node.data.diagramNode.width,
    height: node.measured?.height ?? node.height ?? node.data.diagramNode.height,
  })
}
