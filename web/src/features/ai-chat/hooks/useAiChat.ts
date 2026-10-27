import { type InfiniteData, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { aiChatApi } from '../api/aiChatApi'
import { aiMessagesKey, appendToNewestPage, chronologicalMessages, createLogicalSend } from '../aiChatState'
import type { AiSkill, PagedAiMessageResponse, SendAiMessageRequest } from '../types/aiMessage'
import { useAiChatRealtime } from './useAiChatRealtime'

export function useAiChat({ projectId, sessionId, modelId, diagramId, enabled }: {
  projectId: string
  sessionId?: string
  modelId: string
  diagramId?: string
  enabled: boolean
}) {
  const queryClient = useQueryClient()
  const [failedRequest, setFailedRequest] = useState<SendAiMessageRequest | null>(null)
  const query = useInfiniteQuery({
    queryKey: aiMessagesKey(projectId, sessionId ?? ''),
    queryFn: ({ pageParam }) => aiChatApi.getMessages(projectId, sessionId!, pageParam, 50),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.page + 1,
    enabled: enabled && Boolean(projectId && sessionId),
    retry: false,
  })
  const sendMutation = useMutation({
    mutationFn: (request: SendAiMessageRequest) => aiChatApi.sendMessage(projectId, sessionId!, request),
    onSuccess: (response) => {
      setFailedRequest(null)
      queryClient.setQueryData<InfiniteData<PagedAiMessageResponse>>(
        aiMessagesKey(projectId, sessionId!),
        (current) => current ? {
          ...current,
          pages: appendToNewestPage(current.pages, [response.userMessage, response.assistantMessage]),
        } : current,
      )
    },
    onError: (_error, request) => setFailedRequest(request),
  })
  const realtime = useAiChatRealtime(projectId, sessionId, enabled)

  const send = (content: string, skill: AiSkill) => sendMutation.mutate(createLogicalSend(
    content, modelId || undefined, diagramId || undefined, skill))
  const retry = () => { if (failedRequest) sendMutation.mutate(failedRequest) }

  return {
    query,
    messages: chronologicalMessages(query.data?.pages ?? []),
    send,
    retry,
    failedRequest,
    isSending: sendMutation.isPending,
    sendError: sendMutation.error,
    connected: realtime.connected,
  }
}
