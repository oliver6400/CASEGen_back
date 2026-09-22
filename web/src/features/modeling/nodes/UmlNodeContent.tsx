import { Handle, NodeResizer, Position } from '@xyflow/react'
import type { UmlNodeData } from '../mapper/umlToFlow'
import type { UmlOperationView, UmlParameter, UmlProperty, UmlTypeKind, UmlVisibility } from '../types/uml'

const visibilitySymbol: Record<UmlVisibility, string> = {
  PUBLIC: '+', PRIVATE: '-', PROTECTED: '#', PACKAGE: '~',
}
const primitiveLabel: Record<string, string> = {
  STRING: 'String', INTEGER: 'Integer', BOOLEAN: 'Boolean', REAL: 'Real',
  UNLIMITED_NATURAL: 'UnlimitedNatural',
}

function typeName(typeKind: UmlTypeKind, primitiveType: string | null, typeClassifierId: string | null, names: Record<string, string>) {
  if (typeKind === 'PRIMITIVE') return primitiveType ? primitiveLabel[primitiveType] : '?'
  return (typeClassifierId && names[typeClassifierId]) || 'Clase / Interfaz'
}

function multiplicity(lower: number, upper: number | null) {
  if (lower === 1 && upper === 1) return ''
  return ` [${lower}..${upper === null ? '*' : upper}]`
}

function propertyLabel(property: UmlProperty, names: Record<string, string>) {
  const derived = property.isDerived ? '/' : ''
  const label = `${visibilitySymbol[property.visibility]} ${derived}${property.name} : ${typeName(property.typeKind, property.primitiveType, property.typeClassifierId, names)}${multiplicity(property.lowerBound, property.upperBound)}`
  return property.isStatic ? <u>{label}</u> : label
}

function parameterLabel(parameter: UmlParameter, names: Record<string, string>) {
  const name = parameter.direction === 'RETURN' ? '' : `${parameter.name || 'value'} : `
  return `${name}${typeName(parameter.typeKind, parameter.primitiveType, parameter.typeClassifierId, names)}${multiplicity(parameter.lowerBound, parameter.upperBound)}`
}

function operationLabel(operation: UmlOperationView, names: Record<string, string>) {
  const params = operation.parameters.filter((item) => item.direction !== 'RETURN').sort((a, b) => a.position - b.position)
  const returnParameter = operation.parameters.find((item) => item.direction === 'RETURN')
  const label = `${visibilitySymbol[operation.visibility]} ${operation.name}(${params.map((item) => parameterLabel(item, names)).join(', ')})${returnParameter ? `: ${parameterLabel(returnParameter, names)}` : ''}`
  return operation.isStatic ? <u>{label}</u> : label
}

export function UmlNodeContent({ data, stereotype }: { data: UmlNodeData; stereotype?: string }) {
  const { classifier, classifierNames } = data
  const collaborationColor = data.remotePresence[0]?.color ?? data.highlight?.color
  return (
    <article className={`uml-node relative h-full w-full ${data.selected ? 'uml-node-selected' : ''}`} style={!data.selected && collaborationColor ? { borderColor: collaborationColor, boxShadow: `0 0 0 4px ${collaborationColor}33` } : undefined} title={data.highlight?.message}>
      <NodeResizer
        color="#4f46e5"
        isVisible={data.canEdit && data.selected}
        minHeight={120}
        minWidth={220}
        onResizeEnd={(_, geometry) => data.onGeometryCommit(data.diagramNode, geometry)}
      />
      {data.anchors.map((anchor) => {
        const position = ({ TOP: Position.Top, RIGHT: Position.Right, BOTTOM: Position.Bottom, LEFT: Position.Left })[anchor.side]
        const percentage = `${((anchor.positionIndex + 1) / (anchor.sideCount + 1)) * 100}%`
        const style = anchor.side === 'TOP' || anchor.side === 'BOTTOM' ? { left: percentage } : { top: percentage }
        return <Handle className="uml-handle" id={anchor.id} key={anchor.id} position={position} style={style} type={anchor.type} />
      })}
      <header className="uml-compartment text-center">
        {stereotype && <div className="text-[11px] font-normal">{stereotype}</div>}
        <strong className={classifier.isAbstract ? 'italic' : ''}>{classifier.name}</strong>
      </header>
      {data.remotePresence.length > 0 && <div className="absolute right-1 top-1 z-20 flex max-w-[70%] flex-wrap justify-end gap-1">
        {data.remotePresence.map((participant) => <span className="rounded-full px-1.5 py-0.5 font-sans text-[9px] font-bold text-white shadow" key={participant.userId} style={{ backgroundColor: participant.color }} title={`${participant.displayName} está ${participant.activity === 'EDITING' ? 'editando' : 'seleccionando'} ${classifier.name}`}>{participant.displayName}</span>)}
      </div>}
      <div className="uml-compartment min-h-12">
        {classifier.properties.length === 0 ? <span className="uml-empty">sin atributos</span> : classifier.properties.map((property) => <div key={property.id}>{propertyLabel(property, classifierNames)}</div>)}
      </div>
      <div className="uml-compartment min-h-12">
        {classifier.operations.length === 0 ? <span className="uml-empty">sin operaciones</span> : classifier.operations.map((operation) => <div key={operation.id}>{operationLabel(operation, classifierNames)}</div>)}
      </div>
    </article>
  )
}
