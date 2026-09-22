import { httpClient } from '../../../api/httpClient'

export type SharingRole = 'COLLABORATOR' | 'VIEWER'
export interface ProjectMember {
  id: string
  userId: string
  displayName: string
  email: string | null
  role: 'OWNER' | SharingRole
  status: 'ACTIVE' | 'INVITED' | 'REVOKED'
}
export interface ProjectInvitation {
  id: string
  projectId: string
  projectName: string
  email: string
  role: SharingRole
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'REVOKED' | 'EXPIRED'
  expiresAt: string
  createdAt: string
}
export const sharingApi = {
  visibility: async (projectId: string, visibility: 'PRIVATE' | 'PUBLIC') =>
    (await httpClient.patch(`/api/projects/${projectId}/visibility`, { visibility })).data,
  members: async (projectId: string) =>
    (await httpClient.get<ProjectMember[]>(`/api/projects/${projectId}/members`)).data,
  role: async (projectId: string, memberId: string, role: SharingRole) =>
    (await httpClient.patch(`/api/projects/${projectId}/members/${memberId}/role`, { role })).data,
  revokeMember: async (projectId: string, memberId: string) =>
    (await httpClient.delete(`/api/projects/${projectId}/members/${memberId}`)).data,
  invitations: async (projectId: string) =>
    (await httpClient.get<ProjectInvitation[]>(`/api/projects/${projectId}/invitations`)).data,
  invite: async (projectId: string, email: string, role: SharingRole) =>
    (await httpClient.post(`/api/projects/${projectId}/invitations`, { email, role })).data,
  mine: async () => (await httpClient.get<ProjectInvitation[]>('/api/me/project-invitations')).data,
  accept: async (id: string) => (await httpClient.post(`/api/project-invitations/${id}/accept`)).data,
  reject: async (id: string) => (await httpClient.post(`/api/project-invitations/${id}/reject`)).data,
  revokeInvitation: async (projectId: string, id: string) =>
    (await httpClient.post(`/api/projects/${projectId}/invitations/${id}/revoke`)).data,
}
