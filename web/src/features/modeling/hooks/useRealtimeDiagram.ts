import { Client, type IMessage } from '@stomp/stompjs'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useAuthStore } from '../../auth/authStore'
import type { PresenceActivity, PresenceParticipant, PresenceSnapshot } from '../api/collaborationApi'
import { collaborationApi } from '../api/collaborationApi'
import type { ClassDiagram, UmlClassifierView } from '../types/uml'

export type RealtimeEventType =
  | 'PACKAGE_CREATED' | 'PACKAGE_UPDATED' | 'PACKAGE_DELETED'
  | 'CLASSIFIER_CREATED' | 'CLASSIFIER_UPDATED' | 'CLASSIFIER_DELETED'
  | 'PROPERTY_CREATED' | 'PROPERTY_UPDATED' | 'PROPERTY_DELETED'
  | 'OPERATION_CREATED' | 'OPERATION_UPDATED' | 'OPERATION_DELETED'
  | 'PARAMETER_CREATED' | 'PARAMETER_UPDATED' | 'PARAMETER_DELETED'
  | 'RELATIONSHIP_CREATED' | 'RELATIONSHIP_UPDATED' | 'RELATIONSHIP_DELETED'
  | 'DIAGRAM_NODE_ADDED' | 'DIAGRAM_NODE_MOVED' | 'DIAGRAM_NODE_RESIZED' | 'DIAGRAM_NODE_REMOVED'
  | 'DIAGRAM_EDGE_ADDED' | 'DIAGRAM_EDGE_UPDATED' | 'DIAGRAM_EDGE_REMOVED'
  | 'DIAGRAM_CREATED' | 'DIAGRAM_DELETED' | 'MODEL_VALIDATION_CHANGED'

export interface RealtimeEvent {
  eventId: string
  eventType: RealtimeEventType
  projectId: string
  modelId: string
  diagramId: string | null
  entityId: string | null
  modelRevision: number | null
  diagramRevision: number | null
  actorUserId: string
  actorDisplayName?: string | null
  description?: string | null
  occurredAt: string
}

export interface CollaborationHighlight {
  elementId: string
  actorUserId: string
  actorDisplayName: string
  color: string
  message: string
}

const DIAGRAM_EVENTS = new Set<RealtimeEventType>([
  'DIAGRAM_NODE_ADDED', 'DIAGRAM_NODE_MOVED', 'DIAGRAM_NODE_RESIZED', 'DIAGRAM_NODE_REMOVED',
  'DIAGRAM_EDGE_ADDED', 'DIAGRAM_EDGE_UPDATED', 'DIAGRAM_EDGE_REMOVED',
])

export function useRealtimeDiagram({ modelId, diagramId, currentUserId, selectedElementId,
  presenceActivity, classifiers, diagram }: {
  modelId: string
  diagramId: string | null
  currentUserId: string | null
  selectedElementId: string | null
  presenceActivity: PresenceActivity
  classifiers: UmlClassifierView[]
  diagram?: ClassDiagram
}) {
  const queryClient = useQueryClient()
  const accessToken = useAuthStore((state) => state.accessToken)
  const seenEvents = useRef(new Set<string>())
  const clientRef = useRef<Client | null>(null)
  const commandRef = useRef({ selectedElementId, activity: presenceActivity })
  const participantsRef = useRef<PresenceParticipant[]>([])
  const classifiersRef = useRef(classifiers)
  const diagramRef = useRef(diagram)
  const currentUserRef = useRef(currentUserId)
  const [participants, setParticipants] = useState<PresenceParticipant[]>([])
  const [highlights, setHighlights] = useState<CollaborationHighlight[]>([])
  const activity = useQuery({
    queryKey: ['collaboration-activity', diagramId],
    queryFn: () => collaborationApi.activity(diagramId!),
    enabled: Boolean(diagramId),
  })
  useEffect(() => {
    commandRef.current = { selectedElementId, activity: presenceActivity }
    participantsRef.current = participants
    classifiersRef.current = classifiers
    diagramRef.current = diagram
    currentUserRef.current = currentUserId
  }, [selectedElementId, presenceActivity, participants, classifiers, diagram, currentUserId])

  useEffect(() => {
    if (!modelId || !diagramId) return
    const refreshAfterConflict = () => {
      void queryClient.invalidateQueries({ queryKey: ['uml-models', modelId, 'semantic'] })
      void queryClient.invalidateQueries({ queryKey: ['diagrams', diagramId] })
    }
    window.addEventListener('casegen:concurrent-modification', refreshAfterConflict)
    const client = new Client({
      brokerURL: websocketUrl(),
      connectHeaders: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
    })
    clientRef.current = client

    const receive = (message: IMessage) => {
      const event = JSON.parse(message.body) as RealtimeEvent
      if (seenEvents.current.has(event.eventId)) return
      seenEvents.current.add(event.eventId)
      if (seenEvents.current.size > 256) {
        const oldest = seenEvents.current.values().next().value
        if (oldest) seenEvents.current.delete(oldest)
      }
      invalidateForEvent(queryClient, modelId, diagramId, event)
      void queryClient.invalidateQueries({ queryKey: ['collaboration-activity', diagramId] })
      if (event.actorUserId !== currentUserRef.current && event.entityId) {
        const actor = participantsRef.current.find((item) => item.userId === event.actorUserId)
        const actorDisplayName = event.actorDisplayName ?? actor?.displayName ?? 'Colaborador'
        const elementId = visualElementId(event.entityId, classifiersRef.current, diagramRef.current)
        const highlight: CollaborationHighlight = {
          elementId,
          actorUserId: event.actorUserId,
          actorDisplayName,
          color: collaboratorColor(event.actorUserId),
          message: event.description ?? changeMessage(actorDisplayName, event.eventType),
        }
        setHighlights((current) => [...current.filter((item) => item.elementId !== elementId), highlight])
        window.setTimeout(() => setHighlights((current) => current.filter((item) => item !== highlight)), 4000)
      }
    }

    client.onConnect = () => {
      client.subscribe(`/topic/models/${modelId}`, receive)
      client.subscribe(`/topic/diagrams/${diagramId}`, receive)
      client.subscribe(`/topic/diagrams/${diagramId}/presence`, (message) => {
        const snapshot = JSON.parse(message.body) as PresenceSnapshot
        setParticipants(snapshot.participants)
      })
      publishPresence(client, diagramId, commandRef.current)
    }
    client.activate()
    const heartbeat = window.setInterval(() => publishPresence(client, diagramId, commandRef.current), 15_000)
    return () => {
      window.clearInterval(heartbeat)
      window.removeEventListener('casegen:concurrent-modification', refreshAfterConflict)
      clientRef.current = null
      setParticipants([])
      void client.deactivate()
    }
  }, [accessToken, diagramId, modelId, queryClient])

  useEffect(() => {
    if (diagramId && clientRef.current) publishPresence(clientRef.current, diagramId, commandRef.current)
  }, [diagramId, presenceActivity, selectedElementId])

  return { participants, activity: activity.data ?? [], highlights }
}

function publishPresence(client: Client, diagramId: string, command: { selectedElementId: string | null; activity: PresenceActivity }) {
  if (!client.connected) return
  client.publish({ destination: `/app/diagrams/${diagramId}/presence`, body: JSON.stringify(command) })
}

function invalidateForEvent(queryClient: ReturnType<typeof useQueryClient>, modelId: string, diagramId: string, event: RealtimeEvent) {
  if (event.eventType === 'DIAGRAM_CREATED' || event.eventType === 'DIAGRAM_DELETED') {
    void queryClient.invalidateQueries({ queryKey: ['uml-models', modelId, 'diagrams'] })
  }
  if (DIAGRAM_EVENTS.has(event.eventType) && event.diagramId) {
    void queryClient.invalidateQueries({ queryKey: ['diagrams', event.diagramId] })
    return
  }
  void queryClient.invalidateQueries({ queryKey: ['uml-models', modelId, 'semantic'] })
  void queryClient.invalidateQueries({ queryKey: ['model-validation', modelId] })
  if (event.eventType.startsWith('RELATIONSHIP_') || event.eventType.startsWith('CLASSIFIER_')) {
    void queryClient.invalidateQueries({ queryKey: ['diagrams', diagramId] })
  }
}

function visualElementId(entityId: string, classifiers: UmlClassifierView[], diagram?: ClassDiagram) {
  const node = diagram?.nodes.find((item) => item.id === entityId)
  if (node) return node.umlElementId
  const edge = diagram?.edges.find((item) => item.id === entityId)
  if (edge) return edge.umlElementId
  for (const classifier of classifiers) {
    if (classifier.id === entityId) return classifier.id
    if (classifier.properties.some((item) => item.id === entityId)) return classifier.id
    for (const operation of classifier.operations) {
      if (operation.id === entityId || operation.parameters.some((item) => item.id === entityId)) return classifier.id
    }
  }
  return entityId
}

function changeMessage(actor: string, type: RealtimeEventType) {
  if (type.endsWith('_CREATED') || type.endsWith('_ADDED')) return `${actor} agregó un elemento`
  if (type.endsWith('_DELETED') || type.endsWith('_REMOVED')) return `${actor} eliminó un elemento`
  if (type === 'DIAGRAM_NODE_MOVED') return `${actor} movió el elemento`
  return `${actor} actualizó el elemento`
}

const COLORS = ['#dc2626', '#2563eb', '#059669', '#d97706', '#7c3aed', '#db2777', '#0891b2', '#65a30d']

export function collaboratorColor(userId: string) {
  let hash = 0
  for (const character of userId) hash = ((hash << 5) - hash + character.charCodeAt(0)) | 0
  return COLORS[Math.abs(hash) % COLORS.length]
}

export function initials(displayName: string) {
  return displayName.trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || '?'
}

function websocketUrl() {
  const configured = import.meta.env.VITE_WS_URL?.trim()
  if (configured) return configured.replace(/\/+$/, '')
  const apiBase = import.meta.env.VITE_API_BASE_URL?.trim()
  if (apiBase) {
    const url = new URL(apiBase, window.location.origin)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    url.pathname = `${url.pathname.replace(/\/+$/, '')}/ws`
    return url.toString()
  }
  return `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`
}
