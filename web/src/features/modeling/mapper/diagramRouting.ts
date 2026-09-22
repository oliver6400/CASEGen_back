import type { DiagramEdgeLayoutInput } from '../api/diagramApi'
import type { ClassDiagram, DiagramEdge, DiagramNode, DiagramSide } from '../types/uml'

export function allocateEdgeLayout(
  diagram: ClassDiagram,
  source: DiagramNode,
  target: DiagramNode,
): DiagramEdgeLayoutInput {
  const [sourceSide, targetSide] = preferredSides(source, target)
  return {
    sourceSide,
    targetSide,
    sourceAnchorIndex: nextAnchorIndex(diagram, source.id, sourceSide),
    targetAnchorIndex: nextAnchorIndex(diagram, target.id, targetSide),
    waypoints: [],
  }
}

function preferredSides(source: DiagramNode, target: DiagramNode): [DiagramSide, DiagramSide] {
  if (source.id === target.id) return ['RIGHT', 'TOP']
  const sourceCenter = { x: source.x + source.width / 2, y: source.y + source.height / 2 }
  const targetCenter = { x: target.x + target.width / 2, y: target.y + target.height / 2 }
  const dx = targetCenter.x - sourceCenter.x
  const dy = targetCenter.y - sourceCenter.y
  if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? ['RIGHT', 'LEFT'] : ['LEFT', 'RIGHT']
  return dy >= 0 ? ['BOTTOM', 'TOP'] : ['TOP', 'BOTTOM']
}

/** Re-evaluates the border sides after a node move while preserving free anchors. */
export function reallocateEdgeLayout(
  diagram: ClassDiagram,
  edge: DiagramEdge,
  nodes: DiagramNode[],
): DiagramEdgeLayoutInput {
  const source = nodes.find((node) => node.id === edge.sourceNodeId)
  const target = nodes.find((node) => node.id === edge.targetNodeId)
  if (!source || !target) throw new Error('No se encontraron los extremos gráficos de la relación.')
  const [sourceSide, targetSide] = preferredSides(source, target)
  return {
    sourceSide,
    targetSide,
    sourceAnchorIndex: sourceSide === edge.sourceSide
      ? edge.sourceAnchorIndex
      : nextAnchorIndex(diagram, source.id, sourceSide, edge.id),
    targetAnchorIndex: targetSide === edge.targetSide
      ? edge.targetAnchorIndex
      : nextAnchorIndex(diagram, target.id, targetSide, edge.id),
    waypoints: [],
  }
}

function nextAnchorIndex(diagram: ClassDiagram, nodeId: string, side: DiagramSide, excludedEdgeId?: string) {
  const indexes = diagram.edges.filter((edge) => edge.id !== excludedEdgeId).flatMap((edge) => {
    const result: number[] = []
    if (edge.sourceNodeId === nodeId && edge.sourceSide === side) result.push(edge.sourceAnchorIndex)
    if (edge.targetNodeId === nodeId && edge.targetSide === side) result.push(edge.targetAnchorIndex)
    return result
  })
  return indexes.length === 0 ? 0 : Math.max(...indexes) + 1
}
