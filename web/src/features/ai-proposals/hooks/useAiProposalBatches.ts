import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { aiProposalBatchApi } from '../api/aiProposalBatchApi'

export const aiProposalBatchesKey=(projectId:string)=>['projects',projectId,'ai-proposal-batches'] as const
export function useAiProposalBatches(projectId:string,enabled:boolean){return useQuery({queryKey:aiProposalBatchesKey(projectId),queryFn:()=>aiProposalBatchApi.list(projectId,0,50),enabled:enabled&&Boolean(projectId),retry:false})}
export function useGenerateAiProposalBatch(projectId:string){const client=useQueryClient();return useMutation({mutationFn:({sessionId,messageId,clientBatchId}:{sessionId:string;messageId:string;clientBatchId:string})=>aiProposalBatchApi.generate(projectId,sessionId,messageId,clientBatchId),onSuccess:()=>client.invalidateQueries({queryKey:aiProposalBatchesKey(projectId)})})}
export function useApplyAiProposalBatch(projectId:string,batchId:string){const client=useQueryClient();return useMutation({mutationFn:(note?:string)=>aiProposalBatchApi.apply(projectId,batchId,note),onSettled:()=>client.invalidateQueries({queryKey:aiProposalBatchesKey(projectId)})})}
