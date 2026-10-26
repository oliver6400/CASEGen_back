import { test } from 'node:test'
import assert from 'node:assert/strict'
import { canAccessAiSessions, canManageAiSession, currentSessionKey, formatSessionDate, normalizeCurrentSession, sessionHistoryKey, shouldJoinSession } from '../src/features/ai-sessions/aiSessionUi.ts'

const access = (projectRole, systemRole = 'USER') => ({ projectRole, systemRole })
const session = (participants = [], status = 'ACTIVE') => ({ id: 'session-1', status, participants })

test('owner can manage a session', () => assert.equal(canManageAiSession(access('OWNER')), true))
test('collaborator can manage a session', () => assert.equal(canManageAiSession(access('COLLABORATOR')), true))
test('viewer cannot manage a session', () => assert.equal(canManageAiSession(access('VIEWER')), false))
test('superuser can manage a session', () => assert.equal(canManageAiSession(access(null, 'SUPERUSER')), true))
test('project member can access sessions', () => assert.equal(canAccessAiSessions(access('VIEWER')), true))
test('external public reader cannot access sessions', () => assert.equal(canAccessAiSessions(access(null)), false))
test('superuser can access sessions', () => assert.equal(canAccessAiSessions(access(null, 'SUPERUSER')), true))
test('missing access cannot access sessions', () => assert.equal(canAccessAiSessions(undefined), false))
test('active session joins a missing user', () => assert.equal(shouldJoinSession(session(), 'user-1'), true))
test('participant is not joined twice', () => assert.equal(shouldJoinSession(session([{ userId: 'user-1' }]), 'user-1'), false))
test('participant comparison uses user id', () => assert.equal(shouldJoinSession(session([{ userId: 'user-2', displayName: 'Same' }]), 'user-1'), true))
test('closed session is never joined', () => assert.equal(shouldJoinSession(session([], 'CLOSED'), 'user-1'), false))
test('missing current session is absence, not a join', () => assert.equal(shouldJoinSession(null, 'user-1'), false))
test('missing current user does not trigger join', () => assert.equal(shouldJoinSession(session(), undefined), false))
test('date formatter accepts UTC instants', () => assert.notEqual(formatSessionDate('2026-10-25T14:00:00Z'), '—'))
test('date formatter represents missing end date', () => assert.equal(formatSessionDate(null), '—'))
test('204 current response is converted to null', () => assert.equal(normalizeCurrentSession(204, session()), null))
test('current query key is scoped to project', () => assert.deepEqual(currentSessionKey('project-1'), ['projects', 'project-1', 'ai-sessions', 'current']))
test('history query key includes page', () => assert.deepEqual(sessionHistoryKey('project-1', 3), ['projects', 'project-1', 'ai-sessions', 'history', 3]))
test('different history pages use different cache keys', () => assert.notDeepEqual(sessionHistoryKey('project-1', 0), sessionHistoryKey('project-1', 1)))
