import { ChevronDown, ChevronUp, LockKeyhole, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { aiSkillLabel } from '../../ai-chat/aiChatState'
import { proposalTypeLabel } from '../aiProposalState'
import { aiProposalBatchesKey, useApplyAiProposalBatch } from '../hooks/useAiProposalBatches'
import { useDecideAiProposal } from '../hooks/useAiProposals'
import type { AiProposalBatchChild, AiProposalBatchResponse } from '../types/aiProposalBatch'
import { AiProposalUmlPreview } from './AiProposalUmlPreview'

export function AiProposalBatchCard({batch,projectId,canEdit}:{batch:AiProposalBatchResponse;projectId:string;canEdit:boolean}){
  const [open,setOpen]=useState(true),[confirmAll,setConfirmAll]=useState(false),[note,setNote]=useState('')
  const applyAll=useApplyAiProposalBatch(projectId,batch.id),stale=batch.currentModelRevision!==batch.modelRevisionCursor
  const pending=batch.children.filter(x=>x.status==='PENDING').length,blocked=batch.children.filter(x=>x.blocked).length,applied=batch.children.filter(x=>x.status==='APPLIED').length
  return <article className="rounded-xl border border-indigo-200 bg-white shadow-sm">
    <header className="flex items-start justify-between gap-2 p-3"><button className="min-w-0 flex-1 text-left" onClick={()=>setOpen(!open)} type="button"><div className="flex items-center gap-2"><strong className="truncate text-sm text-slate-900">Revisión UML · {batch.modelName}</strong>{open?<ChevronUp size={15}/>:<ChevronDown size={15}/>}</div><p className="mt-1 text-xs text-slate-500">{batch.children.length} propuestas · {applied} aplicadas · {pending} pendientes · {blocked} bloqueadas</p><p className="mt-1 text-[11px] text-indigo-600">{batch.provider} · {aiSkillLabel(batch.skill)}</p></button>{canEdit&&pending>0&&<button className="primary-button shrink-0" disabled={stale||applyAll.isPending} onClick={()=>setConfirmAll(true)} type="button">Aplicar todo</button>}</header>
    {open&&<div className="border-t border-indigo-100 p-3">{stale&&<p className="mb-3 flex gap-1 rounded-lg bg-amber-50 p-2 text-xs text-amber-800"><TriangleAlert size={14}/>El modelo cambió desde la última decisión de esta revisión.</p>}<div className="space-y-2">{batch.children.map(child=><BatchChild key={child.id} child={child} siblings={batch.children} projectId={projectId} canEdit={canEdit} stale={stale}/>)}</div></div>}
    {confirmAll&&<div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/35 p-4"><div className="w-full max-w-sm rounded-xl bg-white p-4 shadow-xl"><h3 className="font-semibold">Aplicar revisión completa</h3><p className="mt-2 text-sm text-slate-600">Se aplicarán {pending} propuestas de esta revisión.</p><label className="mt-3 block text-xs font-semibold">Nota opcional<textarea className="mt-1 w-full rounded-lg border p-2 font-normal" value={note} onChange={e=>setNote(e.target.value)}/></label>{applyAll.isError&&<p className="mt-2 text-xs text-red-700">No se pudo aplicar la revisión.</p>}<div className="mt-4 flex justify-end gap-2"><button className="toolbar-button" disabled={applyAll.isPending} onClick={()=>setConfirmAll(false)} type="button">Cancelar</button><button className="primary-button" disabled={applyAll.isPending} onClick={()=>applyAll.mutate(note||undefined,{onSuccess:()=>{setConfirmAll(false);setNote('')}})} type="button">{applyAll.isPending?'Aplicando…':'Aplicar todo'}</button></div></div></div>}
  </article>
}

function BatchChild({child,siblings,projectId,canEdit,stale}:{child:AiProposalBatchChild;siblings:AiProposalBatchChild[];projectId:string;canEdit:boolean;stale:boolean}){
  const client=useQueryClient(),apply=useDecideAiProposal(projectId,child.id,'apply'),reject=useDecideAiProposal(projectId,child.id,'reject'),busy=apply.isPending||reject.isPending
  const refresh=()=>void client.invalidateQueries({queryKey:aiProposalBatchesKey(projectId)}),state=child.blocked?'BLOCKED':child.status
  return <article className="rounded-lg border border-slate-200 bg-slate-50 p-3"><div className="flex items-center justify-between gap-2"><strong className="text-xs text-indigo-700">{proposalTypeLabel(child.proposalType).toUpperCase()}</strong><span className="text-[10px] font-bold text-slate-500">{state}</span></div><AiProposalUmlPreview proposalType={child.proposalType} payload={child.payload} title={child.title} batchChildren={siblings}/>{child.blocked&&child.dependencies.length>0&&<p className="mt-2 flex items-center gap-1 text-xs text-amber-800"><LockKeyhole size={12}/>depende de: {child.dependencies.map(dep=>dep.title).join(', ')}</p>}{child.decidedBy&&<p className="mt-2 text-xs text-slate-500">{child.status==='APPLIED'?'✓':'✕'} {child.decidedBy.name}</p>}{canEdit&&child.status==='PENDING'&&<div className="mt-2 flex gap-2">{!child.blocked&&<button className="primary-button" disabled={busy||stale} onClick={()=>apply.mutate(undefined,{onSettled:refresh})} type="button">Aplicar</button>}<button className="toolbar-button" disabled={busy} onClick={()=>reject.mutate(undefined,{onSettled:refresh})} type="button">Rechazar</button></div>}</article>
}
