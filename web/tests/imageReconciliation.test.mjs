import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  imageReconciliationDependencyIssue,
  initialImageReconciliationDecisions,
  summarizeImageReconciliation,
  buildImageImportConfirmation,
} from '../src/features/modeling/lib/imageReconciliation.ts'

const item = (temporaryId, elementKind, suggestedAction, candidates = [], dependencies = []) => ({
  temporaryId, elementKind, detectedName: temporaryId, suggestedAction,
  decisionRequired: suggestedAction !== 'CREATE', reasonCode: 'TEST', explanation: 'test',
  candidates, dependsOnTemporaryIds: dependencies,
})
const candidate = { elementId: 'existing-1', name: 'Producto', classifierKind: 'CLASS', packageId: null, diagramNodeId: null }
const uuidCandidate = { ...candidate, elementId: '11111111-1111-4111-8111-111111111111' }
const preview = (items) => ({ projectId: 'p', targetModelId: 'm', targetDiagramId: 'd', targetPackageId: null, modelRevision: 4, diagramRevision: 7, items, existingNodes: [] })
const proposal = { suggestedModelName: 'Ventas', detectedClassifiers: [], detectedAttributes: [], detectedOperations: [], detectedRelationships: [], warnings: [], confidence: .9 }

test('CREATE MAP SKIP and CONFLICT initialize without inventing candidate UUIDs', () => {
  const value = preview([
    item('new', 'CLASSIFIER', 'CREATE'),
    item('mapped', 'CLASSIFIER', 'MAP_TO_EXISTING', [candidate]),
    item('duplicate', 'ATTRIBUTE', 'SKIP', [candidate]),
    item('conflict', 'CLASSIFIER', 'CONFLICT', [candidate, { ...candidate, elementId: 'existing-2' }]),
  ])
  const decisions = initialImageReconciliationDecisions(value)
  assert.deepEqual(decisions.new, { action: 'CREATE', candidateId: null })
  assert.deepEqual(decisions.mapped, { action: 'MAP_TO_EXISTING', candidateId: 'existing-1' })
  assert.deepEqual(decisions.duplicate, { action: 'SKIP', candidateId: null })
  assert.deepEqual(decisions.conflict, { action: 'CONFLICT', candidateId: null })
})

test('unresolved classifier dependency keeps a relationship pending', () => {
  const value = preview([
    item('source', 'CLASSIFIER', 'CONFLICT', [candidate]),
    item('target', 'CLASSIFIER', 'CREATE'),
    item('relation', 'RELATIONSHIP', 'CREATE', [], ['source', 'target']),
  ])
  const decisions = initialImageReconciliationDecisions(value)
  assert.match(imageReconciliationDependencyIssue(value.items[2], value, decisions), /source/i)
  assert.deepEqual(summarizeImageReconciliation(value, decisions), { create: 1, map: 0, skip: 0, pending: 2 })
})

test('resolved candidate produces stable reconciliation summary', () => {
  const value = preview([
    item('existing', 'CLASSIFIER', 'MAP_TO_EXISTING', [candidate]),
    item('new', 'CLASSIFIER', 'CREATE'),
    item('duplicate', 'ATTRIBUTE', 'SKIP', [candidate], ['existing']),
    item('relation', 'RELATIONSHIP', 'CREATE', [], ['existing', 'new']),
  ])
  assert.deepEqual(summarizeImageReconciliation(value, initialImageReconciliationDecisions(value)),
    { create: 2, map: 1, skip: 1, pending: 0 })
})

test('contextual dialog keeps preview separate and exposes dedicated confirmation endpoint', () => {
  const dialog = readFileSync(new URL('../src/features/modeling/components/ExistingDiagramImageImportDialog.tsx', import.meta.url), 'utf8')
  const api = readFileSync(new URL('../src/features/modeling/api/modelApi.ts', import.meta.url), 'utf8')
  assert.match(dialog, /targetModelId:\s*modelId/)
  assert.match(dialog, /targetDiagramId:\s*diagramId/)
  assert.match(dialog, /expectedModelRevision:\s*modelRevision/)
  assert.match(dialog, /expectedDiagramRevision:\s*diagramRevision/)
  assert.match(api, /image-import\/reconcile-preview/)
  assert.doesNotMatch(dialog, /confirmImageImport/)
  assert.match(api, /image-import\/confirm-existing/)
  assert.match(dialog, /confirmExistingImageImport/)
})

test('proposal edits invalidate preview and cancel performs no confirmation', () => {
  const dialog = readFileSync(new URL('../src/features/modeling/components/ExistingDiagramImageImportDialog.tsx', import.meta.url), 'utf8')
  assert.match(dialog, /const changeProposal[\s\S]*setPreview\(null\)[\s\S]*reconcile\.reset\(\)/)
  assert.match(dialog, /onCancel=\{onClose\}/)
  assert.match(dialog, /Confirmar incorporación/i)
})

test('toolbar keeps XMI PNG SVG and restricts contextual import to editable users', () => {
  const toolbar = readFileSync(new URL('../src/features/modeling/components/ModelingToolbar.tsx', import.meta.url), 'utf8')
  assert.match(toolbar, /Imagen a UML/)
  assert.match(toolbar, /Exportar XMI/)
  assert.match(toolbar, /onExportVisual\('png'\)/)
  assert.match(toolbar, /onExportVisual\('svg'\)/)
  assert.match(toolbar, /\{canEdit && <>[\s\S]*Imagen a UML[\s\S]*<\/>\}/)
})

test('403 and 409 have explicit safe UI states', () => {
  const dialog = readFileSync(new URL('../src/features/modeling/components/ExistingDiagramImageImportDialog.tsx', import.meta.url), 'utf8')
  assert.match(dialog, /status === 403/)
  assert.match(dialog, /status === 409/)
  assert.match(dialog, /Actualizar revisiones para recalcular/)
  assert.match(dialog, /status === 400/)
  assert.match(dialog, /Consultar estado antes de reintentar/)
})

test('confirmation request uses only identifiers and revisions from current preview', () => {
  const value = preview([
    item('new', 'CLASSIFIER', 'CREATE'),
    item('mapped', 'CLASSIFIER', 'MAP_TO_EXISTING', [uuidCandidate]),
    item('duplicate', 'ATTRIBUTE', 'SKIP', [uuidCandidate], ['mapped']),
  ])
  const request = buildImageImportConfirmation(
    value, proposal, initialImageReconciliationDecisions(value),
  )
  assert.equal(request.targetModelId, 'm')
  assert.equal(request.targetDiagramId, 'd')
  assert.equal(request.expectedModelRevision, 4)
  assert.equal(request.expectedDiagramRevision, 7)
  assert.deepEqual(request.decisions, [
    { temporaryId: 'new', action: 'CREATE', candidateId: null },
    { temporaryId: 'mapped', action: 'MAP_TO_EXISTING', candidateId: uuidCandidate.elementId },
    { temporaryId: 'duplicate', action: 'SKIP', candidateId: null },
  ])
})

test('confirmation rejects conflicts, invalid candidates and skipped dependencies locally', () => {
  const conflict = preview([item('pending', 'CLASSIFIER', 'CONFLICT', [uuidCandidate])])
  assert.throws(() => buildImageImportConfirmation(
    conflict, proposal, initialImageReconciliationDecisions(conflict),
  ), /Resuelva/i)

  const mapped = preview([item('mapped', 'CLASSIFIER', 'MAP_TO_EXISTING', [uuidCandidate])])
  assert.throws(() => buildImageImportConfirmation(mapped, proposal, {
    mapped: { action: 'MAP_TO_EXISTING', candidateId: 'not-a-uuid' },
  }), /candidato válido/i)

  const dependent = preview([
    item('owner', 'CLASSIFIER', 'CREATE'),
    item('attribute', 'ATTRIBUTE', 'CREATE', [], ['owner']),
  ])
  assert.throws(() => buildImageImportConfirmation(dependent, proposal, {
    owner: { action: 'SKIP', candidateId: null },
    attribute: { action: 'CREATE', candidateId: null },
  }), /referencia owner/i)
})

test('dialog prevents double submit and refreshes persisted state after success', () => {
  const dialog = readFileSync(new URL('../src/features/modeling/components/ExistingDiagramImageImportDialog.tsx', import.meta.url), 'utf8')
  const page = readFileSync(new URL('../src/features/modeling/pages/ModelEditorPage.tsx', import.meta.url), 'utf8')
  assert.match(dialog, /confirm\.isPending/)
  assert.match(dialog, /disabled=\{summary\.pending > 0 \|\| pending \|\| blocked/)
  assert.match(dialog, /await onConfirmed\(\)/)
  assert.match(page, /onConfirmed=\{refreshSemantic\}/)
})
