import { BaseEdge, EdgeLabelRenderer } from '@xyflow/react'
import type { Edge, EdgeProps } from '@xyflow/react'
import type { UmlAggregationKind } from '../types/uml'
import { AssociationEndpointDecoration } from './EdgeDecorations'
import { straightPath } from './straightPath'
import type { DiagramWaypoint } from '../types/uml'
import { associationLabelPositions } from './associationLabels'

export interface AssociationEdgeData extends Record<string, unknown> {
  diagramEdgeId: string
  firstLabels: { role: string; multiplicity: string }
  secondLabels: { role: string; multiplicity: string }
  firstNavigable: boolean
  secondNavigable: boolean
  firstAggregation: UmlAggregationKind
  secondAggregation: UmlAggregationKind
  waypoints: DiagramWaypoint[]
  collaborationColor?: string
}

export type AssociationFlowEdge = Edge<AssociationEdgeData, 'association'>

export function AssociationEdge({
  id, sourceX, sourceY, targetX, targetY, data, selected,
}: EdgeProps<AssociationFlowEdge>) {
  const route = straightPath({ x: sourceX, y: sourceY }, { x: targetX, y: targetY })
  const color = selected ? '#4f46e5' : data?.collaborationColor ?? '#334155'
  return <>
    <BaseEdge
      id={id}
      path={route.path}
      style={{ stroke: color, strokeWidth: selected || data?.collaborationColor ? 2.5 : 1.6 }}
    />
    <AssociationEndpointDecoration
      aggregation={data?.firstAggregation ?? 'NONE'}
      color={color}
      navigable={data?.firstNavigable ?? false}
      towardX={route.sourceToward.x}
      towardY={route.sourceToward.y}
      x={sourceX}
      y={sourceY}
    />
    <AssociationEndpointDecoration
      aggregation={data?.secondAggregation ?? 'NONE'}
      color={color}
      navigable={data?.secondNavigable ?? false}
      towardX={route.targetToward.x}
      towardY={route.targetToward.y}
      x={targetX}
      y={targetY}
    />
    <EdgeLabelRenderer>
      <EndLabels source={{ x: sourceX, y: sourceY }} target={{ x: targetX, y: targetY }} labels={data?.firstLabels} endpoint="source" />
      <EndLabels source={{ x: sourceX, y: sourceY }} target={{ x: targetX, y: targetY }} labels={data?.secondLabels} endpoint="target" />
    </EdgeLabelRenderer>
  </>
}

function EndLabels({ source, target, labels, endpoint }: {
  source: { x: number; y: number }
  target: { x: number; y: number }
  labels?: { role: string; multiplicity: string }
  endpoint: 'source' | 'target'
}) {
  if (!labels) return null
  const positions = associationLabelPositions(source, target, endpoint, labels.role)
  return <>{(['role', 'multiplicity'] as const).map((kind) => labels[kind] && <div
    className="nodrag nopan pointer-events-none absolute whitespace-nowrap rounded bg-white/90 px-1 font-mono text-[10px] leading-4 text-slate-700"
    key={kind}
    style={{ transform: `translate(${positions[kind].x}px, ${positions[kind].y}px) translate(-50%, -50%) rotate(${positions.angle}deg)` }}
  >{labels[kind]}</div>)}</>
}
