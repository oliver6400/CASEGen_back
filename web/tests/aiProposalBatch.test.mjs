import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parseProjectAiEvent } from '../src/features/ai-chat/projectAiEvents.ts'

const card=readFileSync(new URL('../src/features/ai-proposals/components/AiProposalBatchCard.tsx',import.meta.url),'utf8')
const board=readFileSync(new URL('../src/features/ai-proposals/components/AiProposalBoard.tsx',import.meta.url),'utf8')
const button=readFileSync(new URL('../src/features/ai-proposals/components/CreateProposalBatchButton.tsx',import.meta.url),'utf8')
const realtime=readFileSync(new URL('../src/features/ai-chat/hooks/useProjectAiRealtime.ts',import.meta.url),'utf8')
const hooks=readFileSync(new URL('../src/features/ai-proposals/hooks/useAiProposalBatches.ts',import.meta.url),'utf8')
const messages=readFileSync(new URL('../src/features/ai-chat/components/AiMessageList.tsx',import.meta.url),'utf8')

test('render batch grouped before individuals',()=>{assert.match(board,/Revisiones completas/);assert.ok(board.indexOf('Revisiones completas')<board.indexOf('Propuestas individuales'))})
test('individual proposals remain separate',()=>assert.match(board,/AiProposalCard/))
test('blocked child hides apply but keeps reject',()=>{assert.match(card,/!child\.blocked/);assert.match(card,/>Rechazar</)})
test('dependency uses human title',()=>assert.match(card,/dep\.title/))
test('individual apply reuses decision hook',()=>assert.match(card,/useDecideAiProposal\(projectId,child\.id,'apply'\)/))
test('individual reject reuses decision hook',()=>assert.match(card,/useDecideAiProposal\(projectId,child\.id,'reject'\)/))
test('Apply All invokes batch endpoint hook',()=>assert.match(card,/useApplyAiProposalBatch/))
test('Apply All modal has optional note',()=>{assert.match(card,/Nota opcional/);assert.match(card,/Se aplicarán/)} )
test('stale revision disables apply operations',()=>{assert.match(card,/currentModelRevision!==batch\.modelRevisionCursor/);assert.match(card,/disabled=\{stale/)} )
test('viewer is read only through canEdit',()=>assert.match(card,/canEdit&&child\.status/))
test('batch created realtime is accepted',()=>assert.equal(parseProjectAiEvent(JSON.stringify({eventType:'AI_PROPOSAL_BATCH_CREATED',projectId:'p',batchId:'b'}))?.eventType,'AI_PROPOSAL_BATCH_CREATED'))
test('child applied realtime invalidates batch cache',()=>{assert.match(realtime,/AI_PROPOSAL_APPLIED/);assert.match(realtime,/aiProposalBatchesKey/)} )
test('child rejected realtime invalidates batch cache',()=>assert.match(realtime,/AI_PROPOSAL_REJECTED/))
test('batch applied realtime is accepted',()=>assert.equal(parseProjectAiEvent(JSON.stringify({eventType:'AI_PROPOSAL_BATCH_APPLIED',projectId:'p',batchId:'b'}))?.eventType,'AI_PROPOSAL_BATCH_APPLIED'))
test('batch cache key prevents duplicate cross-project cards',()=>assert.match(hooks,/\['projects',projectId,'ai-proposal-batches'\]/))
test('board availability is independent of active chat session',()=>{assert.doesNotMatch(board,/session\.status/);assert.match(button,/clientBatchId:\s*id/)})
test('UML reviewer exposes one batch action named Generar propuestas',()=>{assert.match(button,/Generar propuestas/);assert.doesNotMatch(messages,/CreateProposalButton/);assert.doesNotMatch(messages,/Generar revisión completa/)})
