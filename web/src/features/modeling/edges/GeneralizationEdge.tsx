import { BaseEdge } from '@xyflow/react'
import type { Edge, EdgeProps } from '@xyflow/react'
import { GeneralizationTriangle } from './EdgeDecorations'
import { straightPath } from './straightPath'
import type { DiagramWaypoint } from '../types/uml'

export type GeneralizationFlowEdge = Edge<{ diagramEdgeId: string; waypoints: DiagramWaypoint[]; collaborationColor?: string }, 'generalization'>

export function GeneralizationEdge({ id, sourceX, sourceY, targetX, targetY, selected, data }: EdgeProps<GeneralizationFlowEdge>) {
  const route = straightPath({ x: sourceX, y: sourceY }, { x: targetX, y: targetY })
  const color = selected ? '#4f46e5' : data?.collaborationColor ?? '#334155'
  return <>
    <BaseEdge id={id} path={route.path} style={{ stroke: color, strokeWidth: selected || data?.collaborationColor ? 2.5 : 1.6 }} />
    <GeneralizationTriangle color={color} towardX={route.targetToward.x} towardY={route.targetToward.y} x={targetX} y={targetY} />
  </>
}
