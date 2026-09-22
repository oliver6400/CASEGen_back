import type { XYPosition } from '@xyflow/react'
import type { UmlClassifierView } from '../types/uml'

const NODE_WIDTH = 280
const COLUMN_GAP = 90
const ROW_GAP = 90
const ORIGIN = 50

export function approximateUmlNodeHeight(classifier: UmlClassifierView) {
  const propertyLines = Math.max(1, classifier.properties.length)
  const operationLines = Math.max(1, classifier.operations.length)
  return 58 + propertyLines * 20 + operationLines * 22
}

export function deterministicUmlLayout(classifiers: UmlClassifierView[]): Record<string, XYPosition> {
  const sorted = [...classifiers].sort((left, right) =>
    left.name.localeCompare(right.name, 'en', { sensitivity: 'base' }) || left.id.localeCompare(right.id),
  )
  const columns = Math.max(1, Math.ceil(Math.sqrt(sorted.length)))
  const positions: Record<string, XYPosition> = {}
  let y = ORIGIN

  for (let rowStart = 0; rowStart < sorted.length; rowStart += columns) {
    const row = sorted.slice(rowStart, rowStart + columns)
    row.forEach((classifier, column) => {
      positions[classifier.id] = { x: ORIGIN + column * (NODE_WIDTH + COLUMN_GAP), y }
    })
    y += Math.max(...row.map(approximateUmlNodeHeight)) + ROW_GAP
  }
  return positions
}
