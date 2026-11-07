import { TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { friendlyDecisionError, proposalStatusLabel, proposalTypeLabel } from '../aiProposalState'
import { useDecideAiProposal } from '../hooks/useAiProposals'
import type { AiProposalResponse } from '../types/aiProposal'
import { AiProposalDecisionModal } from './AiProposalDecisionModal'
import { AiProposalUmlPreview } from './AiProposalUmlPreview'

export function AiProposalCard({proposal,projectId,canEdit,onRefresh,highlighted=false}:{proposal:AiProposalResponse;projectId:string;canEdit:boolean;onRefresh:()=>void;highlighted?:boolean}){
  const [decision,setDecision]=useState<'apply'|'reject'>(),apply=useDecideAiProposal(projectId,proposal.id,'apply'),reject=useDecideAiProposal(projectId,proposal.id,'reject')
  const mutation=decision==='apply'?apply:reject,pending=apply.isPending||reject.isPending,error=apply.error??reject.error
  const decide=(note?:string)=>mutation.mutate(note,{onSuccess:()=>setDecision(undefined),onError:onRefresh})
  return <article className={`rounded-xl border p-3 transition ${highlighted?'border-indigo-400 bg-indigo-50 shadow-sm':'border-[var(--border)] bg-[var(--surface)]'}`}>
    <div className="flex items-center justify-between gap-2"><strong className="text-xs uppercase text-indigo-700">{proposalTypeLabel(proposal.proposalType)}</strong><span className={`rounded-full px-2 py-0.5 text-[10px] font-bold uppercase ${statusStyle(proposal.status)}`}>{proposalStatusLabel(proposal.status)}</span></div>
    <AiProposalUmlPreview proposalType={proposal.proposalType} payload={proposal.payload} title={proposal.title}/>
    {proposal.status==='PENDING'&&(proposal.modelStale||proposal.diagramContextChanged)&&<div className="mt-3 space-y-1 rounded-lg bg-amber-50 p-2 text-xs text-amber-900">{proposal.modelStale&&<p className="flex gap-1.5"><TriangleAlert className="shrink-0" size={14}/>El modelo cambió desde que se creó esta propuesta.</p>}{proposal.diagramContextChanged&&<p className="flex gap-1.5"><TriangleAlert className="shrink-0" size={14}/>El diagrama cambió desde esta propuesta.</p>}</div>}
    {proposal.status!=='PENDING'&&proposal.decidedByName&&<p className="mt-2 text-xs text-slate-500">{proposal.status==='APPLIED'?'✓':'✕'} {proposal.decidedByName}</p>}
    {error&&<p className="mt-3 rounded-lg bg-red-50 p-2 text-sm text-red-700" role="alert">{friendlyDecisionError(error)}</p>}
    {canEdit&&proposal.status==='PENDING'&&<div className="mt-3 flex gap-2 border-t border-slate-100 pt-3"><button className="primary-button flex-1" disabled={pending||proposal.modelStale} onClick={()=>setDecision('apply')} type="button">Aplicar</button><button className="toolbar-button flex-1" disabled={pending} onClick={()=>setDecision('reject')} type="button">Rechazar</button></div>}
    {decision&&<AiProposalDecisionModal proposal={proposal} decision={decision} pending={mutation.isPending} onCancel={()=>!mutation.isPending&&setDecision(undefined)} onConfirm={decide}/>} 
  </article>
}
function statusStyle(status:AiProposalResponse['status']){if(status==='APPLIED')return 'bg-emerald-100 text-emerald-800';if(status==='REJECTED')return 'bg-slate-200 text-slate-700';return 'bg-amber-100 text-amber-800'}
