import axios from 'axios'
import type { InfiniteData } from '@tanstack/react-query'
import type { AiMessageResponse } from '../ai-chat/types/aiMessage'
import type { AiProposalResponse, AiProposalType, PagedAiProposalResponse } from './types/aiProposal'

export const aiProposalsKey = (projectId: string) => ['projects', projectId, 'ai-proposals'] as const
export const proposalCollectionPath = (projectId: string) => `/api/projects/${projectId}/ai/proposals`
export const proposalCreationPath = (projectId: string, sessionId: string, messageId: string) =>
  `/api/projects/${projectId}/ai/sessions/${sessionId}/messages/${messageId}/proposals`
export const proposalDetailPath = (projectId: string, proposalId: string) =>
  `${proposalCollectionPath(projectId)}/${proposalId}`

export function canCreateProposalFromMessage(message: AiMessageResponse) {
  return message.role === 'ASSISTANT' && message.skill === 'UML_REVIEWER'
}

export function proposalAttemptId(current: string | null) {
  return current ?? crypto.randomUUID()
}

export function upsertProposal(current: InfiniteData<PagedAiProposalResponse> | undefined,
  proposal: AiProposalResponse) {
  if (!current) return current
  const without = current.pages.map((page) => ({
    ...page,
    content: page.content.filter((item) => item.id !== proposal.id),
  }))
  const alreadyKnown = current.pages.some((page) => page.content.some((item) => item.id === proposal.id))
  const totalDelta = alreadyKnown ? 0 : 1
  return {
    ...current,
    pages: without.map((page, index) => index === 0 ? {
      ...page,
      content: [proposal, ...page.content].sort(newestFirst),
      totalElements: page.totalElements + totalDelta,
    } : { ...page, totalElements: page.totalElements + totalDelta }),
  }
}

export function flattenProposals(pages: PagedAiProposalResponse[]) {
  const byId = new Map<string, AiProposalResponse>()
  for (const proposal of pages.flatMap((page) => page.content)) byId.set(proposal.id, proposal)
  return [...byId.values()].sort(newestFirst)
}

export function proposalTypeLabel(type: AiProposalType) {
  return ({
    ADD_CLASSIFIER: 'Agregar clase/interfaz',
    ADD_ATTRIBUTE: 'Agregar atributo',
    ADD_OPERATION: 'Agregar operación',
    ADD_ASSOCIATION: 'Agregar asociación',
    ADD_GENERALIZATION: 'Agregar generalización',
    ADD_REALIZATION: 'Agregar realización',
    UPDATE_CLASSIFIER: 'Mejorar clase/interfaz',
    UPDATE_ATTRIBUTE: 'Mejorar atributo',
  })[type]
}

export function proposalStatusLabel(status: AiProposalResponse['status']) {
  return ({ PENDING: 'Pendiente', APPLIED: 'Aplicada', REJECTED: 'Rechazada' })[status]
}

export function proposalPayloadSummary(proposal: AiProposalResponse) {
  const payload = proposal.payload
  const text = (key: string) => typeof payload[key] === 'string' ? payload[key] as string : undefined
  switch (proposal.proposalType) {
    case 'ADD_CLASSIFIER': return text('name') ? `Clase ${text('name')}` : proposal.title
    case 'ADD_ATTRIBUTE': {
      const name = text('name')
      const type = text('type') ?? text('typeName')
      const owner = text('classifierName') ?? text('ownerName')
      return name ? `${name}${type ? ` : ${type}` : ''}${owner ? ` en ${owner}` : ''}` : proposal.title
    }
    case 'ADD_OPERATION': {
      const name = text('name')
      const owner = text('classifierName') ?? text('ownerName')
      return name ? `${name}()${owner ? ` en ${owner}` : ''}` : proposal.title
    }
    case 'ADD_GENERALIZATION':
      return relationSummary(payload, ['specificName', 'childName', 'sourceName'],
        ['generalName', 'parentName', 'targetName'], '→', proposal.title)
    case 'ADD_REALIZATION':
      return relationSummary(payload, ['implementingClassifierName', 'sourceName'],
        ['interfaceName', 'targetName'], 'implementa', proposal.title)
    case 'ADD_ASSOCIATION':
      return relationSummary(payload, ['sourceName', 'end1Name'],
        ['targetName', 'end2Name'], '↔', proposal.title)
    case 'UPDATE_CLASSIFIER': return text('name') ? `Modificar ${text('name')}` : proposal.title
    case 'UPDATE_ATTRIBUTE': return text('name') ? `Modificar ${text('name')}` : proposal.title
  }
}

export function friendlyProposalError(error: unknown) {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined
  const code = axios.isAxiosError<{ code?: string }>(error) ? error.response?.data?.code : undefined
  if (code === 'NO_APPLICABLE_UML_PROPOSAL') return 'Esta respuesta no contiene un cambio UML aplicable.'
  if (code === 'CONTEXT_REVISION_CHANGED') return 'El modelo cambió mientras se generaba la propuesta. Inténtalo nuevamente.'
  if (code === 'CLIENT_PROPOSAL_ID_REUSED') return 'Este intento ya fue utilizado para otra propuesta.'
  if (status === 422) return 'La sugerencia no puede convertirse en una propuesta válida para el modelo actual.'
  if (status === 429) return 'El proveedor de IA alcanzó temporalmente su límite.'
  if (status === 502) return 'La IA devolvió una propuesta que CASEGen no pudo procesar.'
  if (status === 503) return 'El proveedor de IA no está disponible.'
  if (status === 504) return 'La generación de la propuesta excedió el tiempo de espera.'
  return 'No se pudo crear la propuesta.'
}

export function friendlyDecisionError(error: unknown) {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined
  const code = axios.isAxiosError<{ code?: string }>(error) ? error.response?.data?.code : undefined
  if (code === 'PROPOSAL_ALREADY_DECIDED') return 'La propuesta ya fue decidida por otro usuario.'
  if (code === 'PROPOSAL_STALE') return 'El modelo cambió desde que se creó esta propuesta y ya no puede aplicarse.'
  if (code === 'PROPOSAL_NO_LONGER_VALID') return 'La propuesta ya no es válida para el estado actual del modelo.'
  if (status === 403) return 'No tienes permiso para decidir esta propuesta.'
  if (status === 400) return 'La nota de decisión no es válida.'
  if (status === 404) return 'La propuesta ya no está disponible.'
  return 'No se pudo completar la decisión.'
}

function relationSummary(payload: Record<string, unknown>, leftKeys: string[], rightKeys: string[],
  separator: string, fallback: string) {
  const find = (keys: string[]) => keys.map((key) => payload[key]).find((value) => typeof value === 'string') as string | undefined
  const left = find(leftKeys)
  const right = find(rightKeys)
  return left && right ? `${left} ${separator} ${right}` : fallback
}

function newestFirst(left: AiProposalResponse, right: AiProposalResponse) {
  return right.createdAt.localeCompare(left.createdAt) || right.id.localeCompare(left.id)
}
