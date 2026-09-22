import type { NodeProps } from '@xyflow/react'
import type { UmlNodeData } from '../mapper/umlToFlow'
import { UmlNodeContent } from './UmlNodeContent'

export function UmlInterfaceNode({ data }: NodeProps) {
  return <UmlNodeContent data={data as UmlNodeData} stereotype="«interface»" />
}
