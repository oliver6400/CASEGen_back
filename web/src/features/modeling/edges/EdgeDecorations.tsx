import type { UmlAggregationKind } from '../types/uml'

interface EndpointDecorationProps {
  x: number
  y: number
  towardX: number
  towardY: number
  aggregation: UmlAggregationKind
  navigable: boolean
  color: string
}

export function AssociationEndpointDecoration({
  x, y, towardX, towardY, aggregation, navigable, color,
}: EndpointDecorationProps) {
  const angle = angleTo(x, y, towardX, towardY)
  const arrowTip = aggregation === 'NONE' ? 0 : 19
  return <g pointerEvents="none" transform={`translate(${x} ${y}) rotate(${angle})`}>
    {aggregation !== 'NONE' && <polygon
      fill={aggregation === 'COMPOSITE' ? color : 'white'}
      points="1,0 9,-7 17,0 9,7"
      stroke={color}
      strokeLinejoin="round"
      strokeWidth="1.5"
    />}
    {navigable && <path
      d={`M ${arrowTip + 13} -5 L ${arrowTip} 0 L ${arrowTip + 13} 5`}
      fill="none"
      stroke={color}
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.5"
    />}
  </g>
}

export function GeneralizationTriangle({
  x, y, towardX, towardY, color,
}: Omit<EndpointDecorationProps, 'aggregation' | 'navigable'>) {
  return <g pointerEvents="none" transform={`translate(${x} ${y}) rotate(${angleTo(x, y, towardX, towardY)})`}>
    <polygon
      fill="white"
      points="1,0 17,-8 17,8"
      stroke={color}
      strokeLinejoin="round"
      strokeWidth="1.5"
    />
  </g>
}

function angleTo(x: number, y: number, towardX: number, towardY: number) {
  return Math.atan2(towardY - y, towardX - x) * 180 / Math.PI
}
