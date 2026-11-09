import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  calculateDiagramExportBounds,
  createDiagramExportPlan,
  safeDiagramFileName,
} from '../src/features/modeling/lib/diagramExport.ts'

test('bounds include every persisted node plus padding', () => {
  const bounds = calculateDiagramExportBounds([
    { x: 100, y: 200, width: 300, height: 180 },
    { x: 2400, y: -500, width: 320, height: 220 },
  ], 50)
  assert.deepEqual(bounds, { x: 50, y: -550, width: 2720, height: 980 })
})

test('export plan is independent of current viewport zoom and pan', () => {
  const plan = createDiagramExportPlan([
    { x: -300, y: 90, width: 280, height: 180 },
    { x: 900, y: 700, width: 280, height: 180 },
  ], 72)
  assert.equal(plan.transform, 'translate(372px, -18px) scale(1)')
  assert.equal(plan.width, 1624)
  assert.equal(plan.height, 934)
})

test('empty diagrams are rejected with a user-facing error', () => {
  assert.throws(() => calculateDiagramExportBounds([]), /no contiene elementos/i)
})

test('unsafe file names become portable PNG and SVG names', () => {
  assert.equal(safeDiagramFileName('  Ventas: Bogotá / Principal?  ', 'png'), 'Ventas-Bogota-Principal.png')
  assert.equal(safeDiagramFileName('***', 'svg'), 'diagrama-uml.svg')
})
