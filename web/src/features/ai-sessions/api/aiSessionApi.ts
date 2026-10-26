import { httpClient } from '../../../api/httpClient'
import type { AiSession, PagedAiSessionResponse } from '../types/aiSession'
import { normalizeCurrentSession } from '../aiSessionUi'

const basePath = (projectId: string) => `/api/projects/${projectId}/ai/sessions`

export const aiSessionApi = {
  getCurrentSession: async (projectId: string): Promise<AiSession | null> => {
    const response = await httpClient.get<AiSession>(`${basePath(projectId)}/current`)
    return normalizeCurrentSession(response.status, response.data)
  },
  startOrReuseSession: async (projectId: string) =>
    (await httpClient.post<AiSession>(basePath(projectId))).data,
  joinSession: async (projectId: string, sessionId: string) =>
    (await httpClient.post<AiSession>(`${basePath(projectId)}/${sessionId}/join`)).data,
  endSession: async (projectId: string, sessionId: string) =>
    (await httpClient.post<AiSession>(`${basePath(projectId)}/${sessionId}/end`)).data,
  getSessionHistory: async (projectId: string, page = 0, size = 20) =>
    (await httpClient.get<PagedAiSessionResponse>(basePath(projectId), { params: { page, size } })).data,
  getSession: async (projectId: string, sessionId: string) =>
    (await httpClient.get<AiSession>(`${basePath(projectId)}/${sessionId}`)).data,
}
