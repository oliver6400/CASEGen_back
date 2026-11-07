import { type InfiniteData, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { aiProposalApi } from '../api/aiProposalApi'
import { aiProposalsKey, flattenProposals, upsertProposal } from '../aiProposalState'
import type { PagedAiProposalResponse } from '../types/aiProposal'

export function useAiProposals(projectId: string, enabled: boolean) {
  const query = useInfiniteQuery({
    queryKey: aiProposalsKey(projectId),
    queryFn: ({ pageParam }) => aiProposalApi.listProposals(projectId, pageParam, 30),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.page + 1,
    enabled: enabled && Boolean(projectId),
    retry: false,
  })
  return { query, proposals: flattenProposals(query.data?.pages ?? []) }
}

export function useCreateAiProposal(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, messageId, clientProposalId }: {
      sessionId: string
      messageId: string
      clientProposalId: string
    }) => aiProposalApi.createProposal(projectId, sessionId, messageId, clientProposalId),
    onSuccess: ({ proposal }) => queryClient.setQueryData<InfiniteData<PagedAiProposalResponse>>(
      aiProposalsKey(projectId), (current) => upsertProposal(current, proposal)),
  })
}

export function useDecideAiProposal(projectId: string, proposalId: string,
  decision: 'apply' | 'reject') {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (note?: string) => decision === 'apply'
      ? aiProposalApi.applyProposal(projectId, proposalId, note)
      : aiProposalApi.rejectProposal(projectId, proposalId, note),
    onSuccess: (proposal) => queryClient.setQueryData<InfiniteData<PagedAiProposalResponse>>(
      aiProposalsKey(projectId), (current) => upsertProposal(current, proposal)),
  })
}
