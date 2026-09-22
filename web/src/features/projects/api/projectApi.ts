import { httpClient } from '../../../api/httpClient'

export interface Project {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
  visibility: 'PRIVATE' | 'PUBLIC'
}

export interface SaveProjectInput {
  name: string
  description?: string | null
}

export type ProjectRole = 'OWNER' | 'COLLABORATOR' | 'VIEWER'

export interface ProjectAccess {
  systemRole: 'USER' | 'SUPERUSER'
  projectRole: ProjectRole | null
  canView: boolean
  canEdit: boolean
  canManageMembers: boolean
  canGenerate: boolean
}

export const projectApi = {
  list: async () =>
    (await httpClient.get<Project[]>('/api/v1/projects')).data,
  get: async (projectId: string) =>
    (await httpClient.get<Project>(`/api/v1/projects/${projectId}`)).data,
  create: async (input: SaveProjectInput) =>
    (await httpClient.post<Project>('/api/v1/projects', input)).data,
  update: async (projectId: string, input: SaveProjectInput) =>
    (await httpClient.put<Project>(`/api/v1/projects/${projectId}`, input)).data,
  access: async (projectId: string) =>
    (await httpClient.get<ProjectAccess>(`/api/projects/${projectId}/access`)).data,
}
