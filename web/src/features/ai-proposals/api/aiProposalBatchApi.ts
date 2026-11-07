import { httpClient } from '../../../api/httpClient'
import type { ApplyAiProposalBatchResponse, CreateAiProposalBatchResponse, AiProposalBatchResponse, PagedAiProposalBatchResponse } from '../types/aiProposalBatch'

const collection=(projectId:string)=>`/api/projects/${projectId}/ai/proposal-batches`
export const aiProposalBatchApi={
  generate:async(projectId:string,sessionId:string,messageId:string,clientBatchId:string)=>(await httpClient.post<CreateAiProposalBatchResponse>(`/api/projects/${projectId}/ai/sessions/${sessionId}/messages/${messageId}/proposal-batches`,{clientBatchId})).data,
  list:async(projectId:string,page=0,size=20)=>(await httpClient.get<PagedAiProposalBatchResponse>(collection(projectId),{params:{page,size}})).data,
  detail:async(projectId:string,batchId:string)=>(await httpClient.get<AiProposalBatchResponse>(`${collection(projectId)}/${batchId}`)).data,
  apply:async(projectId:string,batchId:string,note?:string)=>(await httpClient.post<ApplyAiProposalBatchResponse>(`${collection(projectId)}/${batchId}/apply`,note?{note}:{})).data,
}
