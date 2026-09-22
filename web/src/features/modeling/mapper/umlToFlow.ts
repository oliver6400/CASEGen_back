import type { Edge, Node } from '@xyflow/react'
import type { AssociationFlowEdge } from '../edges/AssociationEdge'
import type { GeneralizationFlowEdge } from '../edges/GeneralizationEdge'
import type { RealizationFlowEdge } from '../edges/RealizationEdge'
import type { ClassDiagram, DiagramNode, DiagramSide, UmlClassifierView, UmlRelationships } from '../types/uml'
import { associationEndLabels } from '../edges/associationLabels'
import type { PresenceParticipant } from '../api/collaborationApi'
import type { CollaborationHighlight } from '../hooks/useRealtimeDiagram'
import { collaboratorColor } from '../hooks/useRealtimeDiagram'

export interface UmlNodeAnchor {
  id: string
  type: 'source' | 'target'
  side: DiagramSide
  anchorIndex: number
  positionIndex: number
  sideCount: number
}

export interface UmlNodeData extends Record<string, unknown> {
  classifier: UmlClassifierView
  classifierNames: Record<string, string>
  diagramNode: DiagramNode
  anchors: UmlNodeAnchor[]
  canEdit: boolean
  onGeometryCommit: (node: DiagramNode, geometry: { x: number; y: number; width: number; height: number }) => void
  selected: boolean
  remotePresence: { userId: string; displayName: string; activity: string; color: string }[]
  highlight?: CollaborationHighlight
}

export function umlToFlow(
  classifiers: UmlClassifierView[],
  relationships: UmlRelationships,
  diagram: ClassDiagram,
  selectedElementId: string | null,
  canEdit: boolean,
  onGeometryCommit: UmlNodeData['onGeometryCommit'],
  participants: PresenceParticipant[] = [],
  currentUserId: string | null = null,
  highlights: CollaborationHighlight[] = [],
): { nodes: Node<UmlNodeData>[]; edges: Edge[] } {
  const classifierNames = Object.fromEntries(classifiers.map((classifier) => [classifier.id, classifier.name]))
  const classifiersById = Object.fromEntries(classifiers.map((classifier) => [classifier.id, classifier]))
  const anchorsByNode = collectNodeAnchors(diagram)
  const nodes = diagram.nodes.flatMap((diagramNode) => {
    const classifier = classifiersById[diagramNode.umlElementId]
    if (!classifier) return []
    return [{
      id: diagramNode.id,
      type: classifier.kind === 'INTERFACE' ? 'umlInterface' : 'umlClass',
      position: { x: diagramNode.x, y: diagramNode.y },
      style: { width: diagramNode.width, height: diagramNode.height },
      zIndex: diagramNode.zIndex,
      data: {
        classifier,
        classifierNames,
        diagramNode,
        anchors: anchorsByNode[diagramNode.id] ?? [],
        canEdit,
        onGeometryCommit,
        selected: selectedElementId === classifier.id,
        remotePresence: participants.filter((participant) => participant.userId !== currentUserId && visualSelectionId(participant.selectedElementId, classifiers) === classifier.id)
          .map((participant) => ({ ...participant, color: collaboratorColor(participant.userId) })),
        highlight: highlights.find((item) => item.elementId === classifier.id),
      },
    }]
  })

  const associationById = Object.fromEntries(relationships.associations.map((item) => [item.id, item]))
  const generalizationById = Object.fromEntries(relationships.generalizations.map((item) => [item.id, item]))
  const realizationById = Object.fromEntries(relationships.realizations.map((item) => [item.id, item]))
  const nodeById = Object.fromEntries(diagram.nodes.map((node) => [node.id, node]))

  const associationEdges: AssociationFlowEdge[] = diagram.edges.flatMap((diagramEdge) => {
    const association = associationById[diagramEdge.umlElementId]
    if (!association) return []
    const sourceElementId = nodeById[diagramEdge.sourceNodeId]?.umlElementId
    const firstEnd = association.ends[0].classifierId === sourceElementId ? association.ends[0] : association.ends[1]
    const secondEnd = firstEnd === association.ends[0] ? association.ends[1] : association.ends[0]
    return [{
      id: association.id,
      source: diagramEdge.sourceNodeId,
      target: diagramEdge.targetNodeId,
      sourceHandle: handleId('source', diagramEdge.sourceSide, diagramEdge.sourceAnchorIndex),
      targetHandle: handleId('target', diagramEdge.targetSide, diagramEdge.targetAnchorIndex),
      selected: selectedElementId === association.id,
      type: 'association',
      data: {
        diagramEdgeId: diagramEdge.id,
        waypoints: diagramEdge.waypoints,
        firstLabels: associationEndLabels(firstEnd),
        secondLabels: associationEndLabels(secondEnd),
        firstNavigable: firstEnd.navigable,
        secondNavigable: secondEnd.navigable,
        firstAggregation: firstEnd.aggregation,
        secondAggregation: secondEnd.aggregation,
        collaborationColor: relationshipColor(association.id, participants, currentUserId, highlights),
      },
    }]
  })
  const generalizationEdges: GeneralizationFlowEdge[] = diagram.edges.flatMap((diagramEdge) => {
    const generalization = generalizationById[diagramEdge.umlElementId]
    if (!generalization) return []
    return [{
      id: generalization.id,
      source: diagramEdge.sourceNodeId,
      target: diagramEdge.targetNodeId,
      sourceHandle: handleId('source', diagramEdge.sourceSide, diagramEdge.sourceAnchorIndex),
      targetHandle: handleId('target', diagramEdge.targetSide, diagramEdge.targetAnchorIndex),
      selected: selectedElementId === generalization.id,
      type: 'generalization',
      data: { diagramEdgeId: diagramEdge.id, waypoints: diagramEdge.waypoints, collaborationColor: relationshipColor(generalization.id, participants, currentUserId, highlights) },
    }]
  })
  const realizationEdges: RealizationFlowEdge[] = diagram.edges.flatMap((diagramEdge) => {
    const realization = realizationById[diagramEdge.umlElementId]
    if (!realization) return []
    return [{
      id: realization.id,
      source: diagramEdge.sourceNodeId,
      target: diagramEdge.targetNodeId,
      sourceHandle: handleId('source', diagramEdge.sourceSide, diagramEdge.sourceAnchorIndex),
      targetHandle: handleId('target', diagramEdge.targetSide, diagramEdge.targetAnchorIndex),
      selected: selectedElementId === realization.id,
      type: 'realization',
      data: { diagramEdgeId: diagramEdge.id, waypoints: diagramEdge.waypoints, collaborationColor: relationshipColor(realization.id, participants, currentUserId, highlights) },
    }]
  })
  return { nodes, edges: [...associationEdges, ...generalizationEdges, ...realizationEdges] }
}

function relationshipColor(id: string, participants: PresenceParticipant[], currentUserId: string | null, highlights: CollaborationHighlight[]) {
  const remote = participants.find((participant) => participant.userId !== currentUserId && participant.selectedElementId === id)
  return remote ? collaboratorColor(remote.userId) : highlights.find((item) => item.elementId === id)?.color
}

function visualSelectionId(selectedId: string | null, classifiers: UmlClassifierView[]) {
  if (!selectedId) return null
  for (const classifier of classifiers) {
    if (classifier.id === selectedId || classifier.properties.some((property) => property.id === selectedId)) return classifier.id
    if (classifier.operations.some((operation) => operation.id === selectedId || operation.parameters.some((parameter) => parameter.id === selectedId))) return classifier.id
  }
  return selectedId
}

function collectNodeAnchors(diagram: ClassDiagram) {
  const raw: Record<string, Omit<UmlNodeAnchor, 'positionIndex' | 'sideCount'>[]> = {}
  for (const edge of diagram.edges) {
    addAnchor(raw, edge.sourceNodeId, 'source', edge.sourceSide, edge.sourceAnchorIndex)
    addAnchor(raw, edge.targetNodeId, 'target', edge.targetSide, edge.targetAnchorIndex)
  }
  return Object.fromEntries(Object.entries(raw).map(([nodeId, anchors]) => {
    const result: UmlNodeAnchor[] = []
    for (const side of ['TOP', 'RIGHT', 'BOTTOM', 'LEFT'] as const) {
      const onSide = anchors.filter((anchor) => anchor.side === side)
        .sort((a, b) => a.anchorIndex - b.anchorIndex || a.id.localeCompare(b.id))
      onSide.forEach((anchor, positionIndex) => result.push({ ...anchor, positionIndex, sideCount: onSide.length }))
    }
    return [nodeId, result]
  }))
}

function addAnchor(
  target: Record<string, Omit<UmlNodeAnchor, 'positionIndex' | 'sideCount'>[]>,
  nodeId: string,
  type: 'source' | 'target',
  side: DiagramSide,
  anchorIndex: number,
) {
  const anchor = { id: handleId(type, side, anchorIndex), type, side, anchorIndex }
  target[nodeId] = [...(target[nodeId] ?? []), anchor]
}

function handleId(type: 'source' | 'target', side: DiagramSide, index: number) {
  return `${type}-${side.toLowerCase()}-${index}`
}
