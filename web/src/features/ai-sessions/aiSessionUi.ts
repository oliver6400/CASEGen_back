import type { ProjectAccess } from '../projects/api/projectApi'
import type { AiSession } from './types/aiSession'

export function canAccessAiSessions(access: ProjectAccess | undefined) {
  return access?.systemRole === 'SUPERUSER' || access?.projectRole != null
}

export function canManageAiSession(access: ProjectAccess | undefined) {
  return access?.systemRole === 'SUPERUSER' || access?.projectRole === 'OWNER' || access?.projectRole === 'COLLABORATOR'
}

export function shouldJoinSession(session: AiSession | null | undefined, userId: string | undefined) {
  return Boolean(session && userId && session.status === 'ACTIVE'
    && !session.participants.some((participant) => participant.userId === userId))
}

export function formatSessionDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

export function normalizeCurrentSession(status: number, data: AiSession) {
  return status === 204 ? null : data
}

export const currentSessionKey = (projectId: string) => ['projects', projectId, 'ai-sessions', 'current'] as const
export const sessionHistoryKey = (projectId: string, page: number) => ['projects', projectId, 'ai-sessions', 'history', page] as const
