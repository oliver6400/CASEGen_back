import { httpClient } from '../../../api/httpClient'
import { proposalCollectionPath, proposalCreationPath, proposalDetailPath } from '../aiProposalState'
import type { CreateAiProposalResponse, PagedAiProposalResponse, AiProposalResponse } from '../types/aiProposal'

export const aiProposalApi = {
  createProposal: async (projectId: string, sessionId: string, messageId: string,
    clientProposalId: string) =>
    (await httpClient.post<CreateAiProposalResponse>(
      proposalCreationPath(projectId, sessionId, messageId),
      { clientProposalId },
    )).data,
  listProposals: async (projectId: string, page = 0, size = 30) =>
    (await httpClient.get<PagedAiProposalResponse>(proposalCollectionPath(projectId), {
      params: { page, size },
    })).data,
  getProposal: async (projectId: string, proposalId: string) =>
    (await httpClient.get<AiProposalResponse>(proposalDetailPath(projectId, proposalId))).data,
  applyProposal: async (projectId: string, proposalId: string, note?: string) =>
    (await httpClient.post<AiProposalResponse>(`${proposalDetailPath(projectId, proposalId)}/apply`,
      note ? { note } : {})).data,
  rejectProposal: async (projectId: string, proposalId: string, note?: string) =>
    (await httpClient.post<AiProposalResponse>(`${proposalDetailPath(projectId, proposalId)}/reject`,
      note ? { note } : {})).data,
}
