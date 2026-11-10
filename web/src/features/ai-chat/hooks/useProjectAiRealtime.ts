import { Client, type IMessage } from '@stomp/stompjs'
import { type InfiniteData, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useAuthStore } from '../../auth/authStore'
import { getValidAccessToken } from '../../../api/httpClient'
import { aiProposalsKey, upsertProposal } from '../../ai-proposals/aiProposalState'
import type { PagedAiProposalResponse } from '../../ai-proposals/types/aiProposal'
import { aiProposalBatchesKey } from '../../ai-proposals/hooks/useAiProposalBatches'
import { acceptsAiEvent, aiMessagesKey, appendToNewestPage } from '../aiChatState'
import { parseProjectAiEvent } from '../projectAiEvents'
import type { PagedAiMessageResponse } from '../types/aiMessage'

export function useProjectAiRealtime(projectId: string, sessionId: string | undefined,
  enabled: boolean) {
  const queryClient = useQueryClient()
  const accessToken = useAuthStore((state) => state.accessToken)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!enabled || !projectId || !accessToken) return
    let connectedBefore = false
    const client = new Client({
      brokerURL: websocketUrl(),
      connectHeaders: {},
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
    })
    client.beforeConnect = async () => {
      const token = await getValidAccessToken()
      client.connectHeaders = { Authorization: `Bearer ${token}` }
    }
    const receive = (message: IMessage) => {
      const event = parseProjectAiEvent(message.body)
      if (!event || event.projectId !== projectId) return
      switch (event.eventType) {
        case 'AI_MESSAGE_CREATED':
          if (!sessionId || !acceptsAiEvent(event, projectId, sessionId)) return
          queryClient.setQueryData<InfiniteData<PagedAiMessageResponse>>(
            aiMessagesKey(projectId, sessionId),
            (current) => current ? {
              ...current,
              pages: appendToNewestPage(current.pages, [event.message]),
            } : current,
          )
          if (event.message.diagramId) {
            void queryClient.invalidateQueries({ queryKey: ['diagrams', event.message.diagramId] })
            void queryClient.invalidateQueries({ queryKey: ['uml-models'] })
          }
          break
        case 'AI_PROPOSAL_CREATED':
        case 'AI_PROPOSAL_APPLIED':
        case 'AI_PROPOSAL_REJECTED':
          if (!event.proposal.batchId) queryClient.setQueryData<InfiniteData<PagedAiProposalResponse>>(
            aiProposalsKey(projectId), (current) => upsertProposal(current, event.proposal))
          void queryClient.invalidateQueries({ queryKey: aiProposalBatchesKey(projectId) })
          break
        case 'AI_PROPOSAL_BATCH_CREATED':
        case 'AI_PROPOSAL_BATCH_APPLIED':
          void queryClient.invalidateQueries({ queryKey: aiProposalBatchesKey(projectId) })
          break
      }
    }
    client.onConnect = () => {
      setConnected(true)
      client.subscribe(`/topic/projects/${projectId}/ai`, receive)
      if (connectedBefore) {
        if (sessionId) void queryClient.invalidateQueries({ queryKey: aiMessagesKey(projectId, sessionId) })
        void queryClient.invalidateQueries({ queryKey: aiProposalsKey(projectId) })
        void queryClient.invalidateQueries({ queryKey: aiProposalBatchesKey(projectId) })
      }
      connectedBefore = true
    }
    client.onWebSocketClose = () => setConnected(false)
    client.onStompError = () => setConnected(false)
    let disposed = false
    const activationTimer = window.setTimeout(() => {
      if (disposed) return
      client.activate()
    }, 0)
    return () => {
      disposed = true
      window.clearTimeout(activationTimer)
      setConnected(false)
      void client.deactivate()
    }
  }, [accessToken, enabled, projectId, queryClient, sessionId])

  return { connected }
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
