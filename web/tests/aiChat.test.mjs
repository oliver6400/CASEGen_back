import { test } from 'node:test'
import assert from 'node:assert/strict'
import { acceptsAiEvent, aiMessagesKey, aiSkillLabel, appendToNewestPage, chronologicalMessages, createLogicalSend, friendlyAiError } from '../src/features/ai-chat/aiChatState.ts'

const message = (id, createdAt, role = 'USER') => ({ id, sessionId: 'session-1', role, authorUserId: 'user', authorName: 'Member', content: id,
  skill: 'GENERAL',
  modelId: null, modelName: null, diagramId: null, diagramName: null, provider: role === 'ASSISTANT' ? 'OLLAMA' : null,
  replyToMessageId: null, createdAt })
const page = (number, content, last = false) => ({ content, page: number, size: 50, totalElements: content.length, totalPages: 2, first: number === 0, last })

test('query key is scoped to project and active session', () => {
  assert.deepEqual(aiMessagesKey('project-1', 'session-1'), ['projects', 'project-1', 'ai-sessions', 'session-1', 'messages'])
})

test('older pages are prepended while every page stays chronological', () => {
  const recent = page(0, [message('m3', '2026-01-01T03:00:00Z'), message('m4', '2026-01-01T04:00:00Z')])
  const older = page(1, [message('m1', '2026-01-01T01:00:00Z'), message('m2', '2026-01-01T02:00:00Z')], true)
  assert.deepEqual(chronologicalMessages([recent, older]).map((item) => item.id), ['m1', 'm2', 'm3', 'm4'])
})

test('GET POST and STOMP copies deduplicate strictly by message id', () => {
  const existing = message('same', '2026-01-01T01:00:00Z')
  const pages = appendToNewestPage([page(0, [existing])], [existing, message('assistant', '2026-01-01T02:00:00Z', 'ASSISTANT')])
  assert.deepEqual(chronologicalMessages(pages).map((item) => item.id), ['same', 'assistant'])
})

test('realtime accepts only created messages from current project and session', () => {
  const base = { eventId: 'e', eventType: 'AI_MESSAGE_CREATED', projectId: 'p1', sessionId: 's1', message: message('m', '2026-01-01T01:00:00Z'), occurredAt: '2026-01-01T01:00:00Z' }
  assert.equal(acceptsAiEvent(base, 'p1', 's1'), true)
  assert.equal(acceptsAiEvent({ ...base, projectId: 'p2' }, 'p1', 's1'), false)
  assert.equal(acceptsAiEvent({ ...base, sessionId: 's2' }, 'p1', 's1'), false)
  assert.equal(acceptsAiEvent({ ...base, eventType: 'OTHER' }, 'p1', 's1'), false)
})

test('logical send trims content and includes current model and diagram exactly once', () => {
  const request = createLogicalSend(' question ', 'model-1', 'diagram-1')
  assert.equal(request.content, 'question')
  assert.equal(request.modelId, 'model-1')
  assert.equal(request.diagramId, 'diagram-1')
  assert.equal(request.skill, 'GENERAL')
  assert.match(request.clientMessageId, /^[0-9a-f-]{36}$/)
  const retry = request
  assert.equal(retry.clientMessageId, request.clientMessageId)
})

test('selected skill is sent and the same logical request preserves it on retry', () => {
  const uml = createLogicalSend('review', 'model-1', 'diagram-1', 'UML_REVIEWER')
  const database = createLogicalSend('tables', 'model-1', 'diagram-2', 'DATABASE_REVIEWER')
  assert.equal(uml.skill, 'UML_REVIEWER')
  assert.equal(database.skill, 'DATABASE_REVIEWER')
  const retry = uml
  assert.equal(retry.skill, 'UML_REVIEWER')
  assert.equal(retry.clientMessageId, uml.clientMessageId)
})

test('skill labels are user-facing and realtime message keeps received skill', () => {
  assert.equal(aiSkillLabel('GENERAL'), 'General')
  assert.equal(aiSkillLabel('UML_REVIEWER'), 'Revisor UML')
  assert.equal(aiSkillLabel('DATABASE_REVIEWER'), 'Revisor BD')
  const eventMessage = { ...message('skill', '2026-01-01T01:00:00Z'), skill: 'UML_REVIEWER' }
  assert.equal(eventMessage.skill, 'UML_REVIEWER')
})

test('provider HTTP failures have safe friendly messages', () => {
  const error = (status) => ({ isAxiosError: true, response: { status } })
  assert.match(friendlyAiError(error(429)), /límite/)
  assert.match(friendlyAiError(error(503)), /no está disponible/)
  assert.match(friendlyAiError(error(504)), /demasiado/)
  assert.match(friendlyAiError(error(502)), /no pudo procesarse/)
})
