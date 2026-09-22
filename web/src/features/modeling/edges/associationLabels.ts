import type { UmlAssociationEnd } from '../types/uml'

export function associationEndLabels(end: UmlAssociationEnd) {
  const upper = end.upperBound === null ? '*' : end.upperBound
  const prefix = ({ PUBLIC: '+', PRIVATE: '-', PROTECTED: '#', PACKAGE: '~' })[end.visibility]
  return {
    role: end.roleName ? `${prefix}${end.roleName}` : '',
    multiplicity: end.lowerBound === upper ? String(end.lowerBound) : `${end.lowerBound}..${upper}`,
  }
}

interface Point { x: number; y: number }

/** Places readable labels on opposite sides of the segment, at each endpoint. */
export function associationLabelPositions(source: Point, target: Point, endpoint: 'source' | 'target', role: string) {
  const dx = target.x - source.x
  const dy = target.y - source.y
  const length = Math.hypot(dx, dy) || 1
  const unit = { x: dx / length, y: dy / length }
  let normal = { x: unit.y, y: -unit.x }
  // Keep the role above the line (left for an exactly vertical line), regardless
  // of semantic source/target ordering.
  if (normal.y > 0 || (Math.abs(normal.y) < 1e-8 && normal.x > 0)) normal = { x: -normal.x, y: -normal.y }
  const distance = Math.min(length / 3, Math.max(44, role.length * 3.2 + 26))
  const origin = endpoint === 'source' ? source : target
  const sign = endpoint === 'source' ? 1 : -1
  const base = { x: origin.x + sign * unit.x * distance, y: origin.y + sign * unit.y * distance }
  let angle = Math.atan2(dy, dx) * 180 / Math.PI
  if (angle > 90) angle -= 180
  if (angle < -90) angle += 180
  return {
    role: { x: base.x + normal.x * 13, y: base.y + normal.y * 13 },
    multiplicity: { x: base.x - normal.x * 13, y: base.y - normal.y * 13 },
    angle,
  }
}
