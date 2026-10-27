import axios from 'axios'
import type { AiMessageEvent, AiMessageResponse, AiSkill, PagedAiMessageResponse, SendAiMessageRequest } from './types/aiMessage'

export const aiMessagesKey = (projectId: string, sessionId: string) =>
  ['projects', projectId, 'ai-sessions', sessionId, 'messages'] as const

export function chronologicalMessages(pages: PagedAiMessageResponse[]) {
  return deduplicateMessages([...pages].reverse().flatMap((page) => page.content))
}

export function deduplicateMessages(messages: AiMessageResponse[]) {
  const byId = new Map<string, AiMessageResponse>()
  for (const message of messages) byId.set(message.id, message)
  return [...byId.values()].sort((a, b) =>
    a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id))
}

export function acceptsAiEvent(event: AiMessageEvent, projectId: string, sessionId: string) {
  return event.eventType === 'AI_MESSAGE_CREATED'
    && event.projectId === projectId && event.sessionId === sessionId
}

export function appendToNewestPage(pages: PagedAiMessageResponse[], additions: AiMessageResponse[]) {
  if (pages.length === 0) return pages
  const known = new Set(pages.flatMap((page) => page.content.map((message) => message.id)))
  const unique = additions.filter((message) => !known.has(message.id))
  if (unique.length === 0) return pages
  return pages.map((page, index) => index === 0
    ? { ...page, content: deduplicateMessages([...page.content, ...unique]), totalElements: page.totalElements + unique.length }
    : page)
}

export function createLogicalSend(content: string, modelId?: string, diagramId?: string,
  skill: AiSkill = 'GENERAL'): SendAiMessageRequest {
  return { clientMessageId: crypto.randomUUID(), content: content.trim(), modelId, diagramId, skill }
}

export function aiSkillLabel(skill: AiSkill) {
  return ({ GENERAL: 'General', UML_REVIEWER: 'Revisor UML', DATABASE_REVIEWER: 'Revisor BD' })[skill]
}

export function friendlyAiError(error: unknown) {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined
  if (status === 429) return 'El proveedor de IA alcanzó su límite. Intenta nuevamente.'
  if (status === 503) return 'El proveedor de IA no está disponible.'
  if (status === 504) return 'La IA tardó demasiado en responder.'
  if (status === 502) return 'La respuesta del proveedor no pudo procesarse.'
  return 'No se pudo completar la consulta a la IA.'
}
