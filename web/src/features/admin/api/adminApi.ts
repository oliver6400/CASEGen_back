import { httpClient } from '../../../api/httpClient'
import type { ProjectRole } from '../../projects/api/projectApi'

export interface AdminUser {
  id: string
  email: string
  displayName: string
  systemRole: 'USER' | 'SUPERUSER'
  enabled: boolean
  createdAt: string
}

export interface AdminProject {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminProjectMembership {
  id: string
  projectId: string
  projectName: string
  userId: string
  userEmail: string
  role: ProjectRole
  status: 'INVITED' | 'ACTIVE' | 'REVOKED'
  joinedAt: string | null
}

export const adminApi = {
  users: async () => (await httpClient.get<AdminUser[]>('/api/admin/users')).data,
  projects: async () => (await httpClient.get<AdminProject[]>('/api/admin/projects')).data,
  memberships: async () => (await httpClient.get<AdminProjectMembership[]>('/api/admin/project-memberships')).data,
}
