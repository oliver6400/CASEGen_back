import { useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { aiSessionApi } from '../api/aiSessionApi'
import { currentSessionKey, shouldJoinSession } from '../aiSessionUi'

export { currentSessionKey, sessionHistoryKey } from '../aiSessionUi'

export function useAiSession(projectId: string, userId: string | undefined, enabled: boolean) {
  const queryClient = useQueryClient()
  const attemptedJoins = useRef(new Set<string>())
  const current = useQuery({
    queryKey: currentSessionKey(projectId),
    queryFn: () => aiSessionApi.getCurrentSession(projectId),
    enabled: enabled && Boolean(projectId),
    retry: false,
  })

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: currentSessionKey(projectId) }),
      queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'ai-sessions', 'history'] }),
    ])
  }

  const start = useMutation({ mutationFn: () => aiSessionApi.startOrReuseSession(projectId), onSuccess: refresh })
  const join = useMutation({
    mutationFn: (sessionId: string) => aiSessionApi.joinSession(projectId, sessionId),
    onSuccess: async (session) => {
      queryClient.setQueryData(currentSessionKey(projectId), session)
      await refresh()
    },
  })
  const end = useMutation({ mutationFn: (sessionId: string) => aiSessionApi.endSession(projectId, sessionId), onSuccess: refresh })

  useEffect(() => {
    const session = current.data
    if (!shouldJoinSession(session, userId) || join.isPending) return
    const attemptKey = `${session!.id}:${userId}`
    if (attemptedJoins.current.has(attemptKey)) return
    attemptedJoins.current.add(attemptKey)
    join.mutate(session!.id)
  }, [current.data, join, userId])

  return { current, start, join, end }
}
