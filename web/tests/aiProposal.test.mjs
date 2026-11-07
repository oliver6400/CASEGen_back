import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { aiProposalsKey, canCreateProposalFromMessage, flattenProposals, friendlyDecisionError, friendlyProposalError, proposalAttemptId, proposalCollectionPath, proposalCreationPath, proposalDetailPath, proposalPayloadSummary, proposalStatusLabel, proposalTypeLabel, upsertProposal } from '../src/features/ai-proposals/aiProposalState.ts'
import { parseProjectAiEvent } from '../src/features/ai-chat/projectAiEvents.ts'

const proposal = (id, createdAt = '2026-10-29T12:00:00Z') => ({ id, projectId: 'p1', sessionId: 's1', sourceMessageId: 'm1', requestedByUserId: 'u1', requestedByName: 'Pedro', status: 'PENDING', proposalType: 'ADD_CLASSIFIER', title: 'Agregar Pedido', rationale: 'Razón', payload: { name: 'Pedido' }, payloadSchemaVersion: 1, modelId: 'model-1', modelName: 'Dominio', diagramId: 'diagram-1', diagramName: 'Clases', baseModelRevision: 1, baseDiagramRevision: 1, currentModelRevision: 1, currentDiagramRevision: 1, modelStale: false, diagramContextChanged: false, provider: 'GEMINI', skill: 'UML_REVIEWER', createdAt, updatedAt: createdAt })
const page = (content) => ({ content, page: 0, size: 30, totalElements: content.length, totalPages: 1, first: true, last: true })
const message = (role, skill) => ({ id: 'm1', sessionId: 's1', role, skill, authorUserId: null, authorName: null, content: 'x', modelId: null, modelName: null, diagramId: null, diagramName: null, provider: null, replyToMessageId: null, createdAt: '' })

test('proposal REST paths use project session message and detail identifiers', () => {
  assert.equal(proposalCreationPath('p', 's', 'm'), '/api/projects/p/ai/sessions/s/messages/m/proposals')
  assert.equal(proposalCollectionPath('p'), '/api/projects/p/ai/proposals')
  assert.equal(proposalDetailPath('p', 'x'), '/api/projects/p/ai/proposals/x')
})

test('create API body contains only clientProposalId', () => {
  const source = readFileSync(new URL('../src/features/ai-proposals/api/aiProposalApi.ts', import.meta.url), 'utf8')
  assert.match(source, /\{ clientProposalId \}/)
  assert.doesNotMatch(source, /\{ clientProposalId,\s*(provider|skill|payload|status|modelId|diagramId)/)
})

test('logical retry preserves clientProposalId and a new action creates another UUID', () => {
  const first = proposalAttemptId(null)
  assert.equal(proposalAttemptId(first), first)
  assert.notEqual(proposalAttemptId(null), first)
})

test('button eligibility is only ASSISTANT UML_REVIEWER', () => {
  assert.equal(canCreateProposalFromMessage(message('ASSISTANT', 'UML_REVIEWER')), true)
  assert.equal(canCreateProposalFromMessage(message('USER', 'UML_REVIEWER')), false)
  assert.equal(canCreateProposalFromMessage(message('ASSISTANT', 'GENERAL')), false)
  assert.equal(canCreateProposalFromMessage(message('ASSISTANT', 'DATABASE_REVIEWER')), false)
})

test('proposal cache is project-scoped, newest-first and deduplicated by proposal id', () => {
  assert.deepEqual(aiProposalsKey('p1'), ['projects', 'p1', 'ai-proposals'])
  const old = proposal('old', '2026-10-29T10:00:00Z')
  const recent = proposal('new', '2026-10-29T11:00:00Z')
  const current = { pages: [page([old])], pageParams: [0] }
  const updated = upsertProposal(current, recent)
  const duplicate = upsertProposal(updated, recent)
  assert.deepEqual(flattenProposals(duplicate.pages).map((item) => item.id), ['new', 'old'])
  assert.equal(duplicate.pages[0].totalElements, 2)
})

test('realtime parser discriminates messages and proposals and ignores unknown events', () => {
  const created = proposal('proposal-1')
  const event = parseProjectAiEvent(JSON.stringify({ eventType: 'AI_PROPOSAL_CREATED', projectId: 'p1', proposal: created }))
  assert.equal(event?.eventType, 'AI_PROPOSAL_CREATED')
  assert.equal(event?.proposal.id, 'proposal-1')
  assert.equal(parseProjectAiEvent(JSON.stringify({ eventType: 'OTHER' })), null)
  assert.equal(parseProjectAiEvent('{bad'), null)
})

test('friendly labels payload summaries and safe errors cover CU-28', () => {
  assert.equal(proposalTypeLabel('ADD_CLASSIFIER'), 'Agregar clase/interfaz')
  assert.equal(proposalStatusLabel('PENDING'), 'Pendiente')
  assert.equal(proposalPayloadSummary(proposal('p')), 'Clase Pedido')
  const error = (status, code) => ({ isAxiosError: true, response: { status, data: { code } } })
  assert.match(friendlyProposalError(error(422, 'NO_APPLICABLE_UML_PROPOSAL')), /no contiene/)
  assert.match(friendlyProposalError(error(409, 'CONTEXT_REVISION_CHANGED')), /modelo cambió/)
  assert.match(friendlyProposalError(error(409, 'CLIENT_PROPOSAL_ID_REUSED')), /intento/)
  for (const status of [429, 502, 503, 504]) assert.notEqual(friendlyProposalError(error(status)), 'No se pudo crear la propuesta.')
})

test('drawer exposes decisions only for pending editable proposal cards', () => {
  const drawer = readFileSync(new URL('../src/features/ai-chat/components/AiCollaborativeDrawer.tsx', import.meta.url), 'utf8')
  const card = readFileSync(new URL('../src/features/ai-proposals/components/AiProposalCard.tsx', import.meta.url), 'utf8')
  assert.match(drawer, />Chat</)
  assert.match(drawer, />Pizarra IA</)
  assert.match(drawer, /AiProposalBoard/)
  assert.match(card, /canEdit&&proposal\.status==='PENDING'/)
  assert.match(card, />Aplicar<\/button>/)
  assert.match(card, />Rechazar<\/button>/)
  assert.match(card, /pending\|\|proposal\.modelStale/)
  assert.doesNotMatch(card, /UmlModelService|createClassifier|createAssociation/)
})

test('operational context warnings are visible only while proposal is pending', () => {
  const card = readFileSync(new URL('../src/features/ai-proposals/components/AiProposalCard.tsx', import.meta.url), 'utf8')
  assert.match(card, /proposal\.status==='PENDING'&&\(proposal\.modelStale\|\|proposal\.diagramContextChanged\)/)
  assert.match(card, /proposal\.status==='APPLIED'/)
  assert.doesNotMatch(card, /baseModelRevision|appliedModelRevision|decisionNote/)
})

test('one project AI subscription dispatches chat and all proposal event types', () => {
  const source = readFileSync(new URL('../src/features/ai-chat/hooks/useProjectAiRealtime.ts', import.meta.url), 'utf8')
  assert.equal((source.match(/client\.subscribe\(/g) ?? []).length, 1)
  assert.match(source, /case 'AI_MESSAGE_CREATED'/)
  assert.match(source, /case 'AI_PROPOSAL_CREATED'/)
  assert.match(source, /case 'AI_PROPOSAL_APPLIED'/)
  assert.match(source, /case 'AI_PROPOSAL_REJECTED'/)
})

test('decision API uses exact endpoints and only sends optional note', () => {
  const source = readFileSync(new URL('../src/features/ai-proposals/api/aiProposalApi.ts', import.meta.url), 'utf8')
  assert.match(source, /proposalDetailPath\(projectId, proposalId\)\}\/apply/)
  assert.match(source, /proposalDetailPath\(projectId, proposalId\)\}\/reject/)
  assert.match(source, /note \? \{ note \} : \{\}/)
  assert.doesNotMatch(source, /\{\s*note,\s*(status|actor|decidedBy|appliedElementId|modelRevision)/)
})

test('realtime parser accepts applied and rejected proposal decisions', () => {
  const value = proposal('decision')
  for (const eventType of ['AI_PROPOSAL_APPLIED', 'AI_PROPOSAL_REJECTED']) {
    const event = parseProjectAiEvent(JSON.stringify({ eventType, projectId: 'p1', proposal: value }))
    assert.equal(event?.eventType, eventType)
    assert.equal(event?.proposal.id, 'decision')
  }
})

test('REST and realtime decisions replace cache entry without moving or duplicating it', () => {
  const recent = proposal('recent', '2026-10-29T13:00:00Z')
  const pending = proposal('decision', '2026-10-29T12:00:00Z')
  const current = { pages: [page([recent, pending])], pageParams: [0] }
  const applied = { ...pending, status: 'APPLIED', decidedByName: 'Ana', appliedModelRevision: 2 }
  const afterRest = upsertProposal(current, applied)
  const afterRealtime = upsertProposal(afterRest, applied)
  assert.deepEqual(flattenProposals(afterRealtime.pages).map((item) => item.id), ['recent', 'decision'])
  assert.equal(afterRealtime.pages[0].content.filter((item) => item.id === 'decision').length, 1)
  assert.equal(afterRealtime.pages[0].content.find((item) => item.id === 'decision').status, 'APPLIED')
})

test('decision conflict and permission messages are safe and specific', () => {
  const error = (status, code) => ({ isAxiosError: true, response: { status, data: { code } } })
  assert.match(friendlyDecisionError(error(409, 'PROPOSAL_ALREADY_DECIDED')), /otro usuario/)
  assert.match(friendlyDecisionError(error(409, 'PROPOSAL_STALE')), /no puede aplicarse/)
  assert.match(friendlyDecisionError(error(422, 'PROPOSAL_NO_LONGER_VALID')), /estado actual/)
  assert.match(friendlyDecisionError(error(403)), /permiso/)
  assert.match(friendlyDecisionError(error(400)), /nota/)
  assert.match(friendlyDecisionError(error(404)), /disponible/)
})
