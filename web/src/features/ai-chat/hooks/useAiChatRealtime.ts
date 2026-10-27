import { Client, type IMessage } from '@stomp/stompjs'
import { type InfiniteData, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useAuthStore } from '../../auth/authStore'
import { acceptsAiEvent, aiMessagesKey, appendToNewestPage } from '../aiChatState'
import type { AiMessageEvent, PagedAiMessageResponse } from '../types/aiMessage'

export function useAiChatRealtime(projectId: string, sessionId: string | undefined, enabled: boolean) {
  const queryClient = useQueryClient()
  const accessToken = useAuthStore((state) => state.accessToken)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!enabled || !projectId || !sessionId || !accessToken) return
    let connectedBefore = false
    const client = new Client({
      brokerURL: websocketUrl(),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
    })
    const receive = (message: IMessage) => {
      const event = JSON.parse(message.body) as AiMessageEvent
      if (!acceptsAiEvent(event, projectId, sessionId)) return
      queryClient.setQueryData<InfiniteData<PagedAiMessageResponse>>(
        aiMessagesKey(projectId, sessionId),
        (current) => current ? { ...current, pages: appendToNewestPage(current.pages, [event.message]) } : current,
      )
    }
    client.onConnect = () => {
      setConnected(true)
      client.subscribe(`/topic/projects/${projectId}/ai`, receive)
      if (connectedBefore) void queryClient.invalidateQueries({ queryKey: aiMessagesKey(projectId, sessionId) })
      connectedBefore = true
    }
    client.onWebSocketClose = () => setConnected(false)
    client.onStompError = () => setConnected(false)
    client.activate()
    return () => { setConnected(false); void client.deactivate() }
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
