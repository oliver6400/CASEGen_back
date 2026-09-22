import { httpClient } from '../../../api/httpClient'

export type PresenceActivity = 'VIEWING' | 'SELECTING' | 'EDITING'

export interface PresenceParticipant {
  userId: string
  displayName: string
  diagramId: string
  selectedElementId: string | null
  activity: PresenceActivity
  lastSeen: string
}

export interface PresenceSnapshot {
  diagramId: string
  participants: PresenceParticipant[]
  occurredAt: string
}

export interface CollaborationActivity {
  id: string
  actorUserId: string
  actorDisplayName: string
  eventType: string
  elementId: string | null
  description: string
  modelRevision: number | null
  diagramRevision: number | null
  createdAt: string
}

export const collaborationApi = {
  activity: async (diagramId: string) =>
    (await httpClient.get<CollaborationActivity[]>(`/api/diagrams/${diagramId}/collaboration/activity`)).data,
}
