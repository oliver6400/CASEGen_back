import { useAiProposals } from '../hooks/useAiProposals'
import { useAiProposalBatches } from '../hooks/useAiProposalBatches'
import { AiProposalCard } from './AiProposalCard'
import { AiProposalBatchCard } from './AiProposalBatchCard'

export function AiProposalBoard({projectId,enabled,highlightedId,canEdit}:{projectId:string;enabled:boolean;highlightedId?:string;canEdit:boolean}){
  const {query,proposals}=useAiProposals(projectId,enabled),batches=useAiProposalBatches(projectId,enabled)
  if(query.isPending||batches.isPending)return <p className="py-10 text-center text-sm text-slate-500">Cargando revisión…</p>
  if(query.isError||batches.isError)return <div className="py-10 text-center text-sm text-red-700"><p>Error al cargar la revisión.</p><button className="toolbar-button mt-3" onClick={()=>{void query.refetch();void batches.refetch()}} type="button">Reintentar</button></div>
  return <div className="min-h-0 flex-1 overflow-y-auto py-3"><h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-500">Revisiones completas</h3>{(batches.data?.content.length??0)===0?<p className="py-5 text-center text-sm text-slate-500">No hay revisiones completas.</p>:<div className="space-y-3">{batches.data?.content.map(batch=><AiProposalBatchCard key={batch.id} batch={batch} projectId={projectId} canEdit={canEdit}/>)}</div>}<h3 className="mb-2 mt-5 text-xs font-bold uppercase tracking-wide text-slate-500">Propuestas individuales</h3>{proposals.length===0?<p className="py-5 text-center text-sm text-slate-500">No hay propuestas individuales.</p>:<div className="space-y-3">{proposals.map(proposal=><AiProposalCard key={proposal.id} proposal={proposal} highlighted={proposal.id===highlightedId} projectId={projectId} canEdit={canEdit} onRefresh={()=>void query.refetch()}/>)}</div>}{query.hasNextPage&&<div className="mt-4 text-center"><button className="toolbar-button" disabled={query.isFetchingNextPage} onClick={()=>void query.fetchNextPage()} type="button">{query.isFetchingNextPage?'Cargando…':'Cargar anteriores'}</button></div>}</div>
}
