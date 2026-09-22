import assert from 'node:assert/strict'
import test from 'node:test'
import { associationEndLabels, associationLabelPositions } from '../src/features/modeling/edges/associationLabels.ts'

test('role prefixes and multiplicities remain separate, including unlimited upper', () => {
  for (const [visibility, prefix] of Object.entries({ PUBLIC: '+', PRIVATE: '-', PROTECTED: '#', PACKAGE: '~' })) {
    assert.deepEqual(associationEndLabels({ roleName: 'customer', visibility, lowerBound: 1, upperBound: 1 }), { role: `${prefix}customer`, multiplicity: '1' })
  }
  assert.deepEqual(associationEndLabels({ roleName: 'sales', visibility: 'PRIVATE', lowerBound: 0, upperBound: null }), { role: '-sales', multiplicity: '0..*' })
  assert.deepEqual(associationEndLabels({ roleName: null, visibility: 'PUBLIC', lowerBound: 1, upperBound: null }), { role: '', multiplicity: '1..*' })
})

test('labels are separated perpendicularly and follow their own endpoint at every angle', () => {
  const source = { x: 100, y: 100 }
  for (const target of [{ x: 500, y: 100 }, { x: 100, y: 500 }, { x: 500, y: 400 }, { x: -200, y: -300 }, { x: 100, y: -300 }]) {
    const vector = { x: target.x - source.x, y: target.y - source.y }
    const ends = ['source', 'target'].map((endpoint) => associationLabelPositions(source, target, endpoint, '+customer'))
    for (const labels of ends) {
      const difference = { x: labels.role.x - labels.multiplicity.x, y: labels.role.y - labels.multiplicity.y }
      assert.ok(Math.abs(difference.x * vector.x + difference.y * vector.y) < 1e-6)
      assert.ok(Math.abs(Math.hypot(difference.x, difference.y) - 26) < 1e-6)
      assert.ok(labels.role.y <= labels.multiplicity.y + 1e-8)
      assert.ok(labels.angle >= -90 && labels.angle <= 90)
    }
    assert.ok(Math.hypot(ends[0].role.x - source.x, ends[0].role.y - source.y) < Math.hypot(ends[0].role.x - target.x, ends[0].role.y - target.y))
    assert.ok(Math.hypot(ends[1].role.x - target.x, ends[1].role.y - target.y) < Math.hypot(ends[1].role.x - source.x, ends[1].role.y - source.y))
  }
})
