import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const preview=readFileSync(new URL('../src/features/ai-proposals/components/AiProposalUmlPreview.tsx',import.meta.url),'utf8')
const batch=readFileSync(new URL('../src/features/ai-proposals/components/AiProposalBatchCard.tsx',import.meta.url),'utf8')
const individual=readFileSync(new URL('../src/features/ai-proposals/components/AiProposalCard.tsx',import.meta.url),'utf8')

test('ADD_CLASSIFIER renders class and interface UML boxes',()=>{assert.match(preview,/case 'ADD_CLASSIFIER'/);assert.match(preview,/kind\)==='INTERFACE'/);assert.match(preview,/«\{stereotype\}»/)})
test('ADD_ATTRIBUTE renders owner attribute type and multiplicity',()=>{assert.match(preview,/case 'ADD_ATTRIBUTE'/);assert.match(preview,/ownerName\(payload\.ownerRef/);assert.match(preview,/multiplicity\(payload\)/)})
test('UPDATE_ATTRIBUTE renders compact before and after lines',()=>{assert.match(preview,/case 'UPDATE_ATTRIBUTE'/);assert.match(preview,/`− \$\{before\}`/);assert.match(preview,/`\+ \$\{next\}`/)})
test('ADD_OPERATION renders operation compartment',()=>{assert.match(preview,/case 'ADD_OPERATION'/);assert.match(preview,/operations=\{/);assert.match(preview,/\$\{name\}\(\)/)})
test('association NONE uses a simple SVG line',()=>{assert.match(preview,/kind==='association'/);assert.match(preview,/aggregation==='NONE'/);assert.match(preview,/<line/)})
test('aggregation SHARED uses an empty diamond',()=>{assert.match(preview,/aggregation==='SHARED'/);assert.match(preview,/fill="white"/)})
test('composition COMPOSITE uses a filled diamond',()=>{assert.match(preview,/aggregation==='COMPOSITE'/);assert.match(preview,/fill="currentColor"/)})
test('generalization uses a hollow triangle',()=>{assert.match(preview,/case 'ADD_GENERALIZATION'/);assert.match(preview,/kind="generalization"/);assert.match(preview,/104,6 116,14 104,22/)})
test('realization uses a dashed line and interface label',()=>{assert.match(preview,/case 'ADD_REALIZATION'/);assert.match(preview,/strokeDasharray/);assert.match(preview,/«interface»/)})
test('multiplicity converts null upper bound to star',()=>{assert.match(preview,/upperBound===null\?'\*'/);assert.match(preview,/\[\$\{lower\?\?0\}\.\./)})
test('blocked dependency is one compact human line',()=>{assert.match(batch,/LockKeyhole/);assert.match(batch,/depende de:/);assert.match(batch,/dep\.title/);assert.doesNotMatch(batch,/Requiere:/)})
test('applied actor is compact',()=>{assert.match(batch,/child\.status==='APPLIED'\?'✓':'✕'/);assert.match(batch,/child\.decidedBy\.name/)})
test('rejected actor is compact',()=>assert.match(individual,/proposal\.status==='APPLIED'\?'✓':'✕'/))
test('unexpected payload has compact fallback',()=>{assert.match(preview,/PreviewFallback/);assert.match(preview,/uml-fallback/)})
test('batch omits long summary',()=>{assert.doesNotMatch(batch,/batch\.summary/);assert.match(batch,/propuestas · \{applied\} aplicadas/)})
test('cards omit rationale and duplicated audit data',()=>{assert.doesNotMatch(batch,/child\.rationale|decisionNote|requestedBy|sourceMessage/);assert.doesNotMatch(individual,/proposal\.rationale|requestedByName|provider|skill|sourceMessage/)})
test('buttons follow pending and blocked states',()=>{assert.match(batch,/child\.status==='PENDING'/);assert.match(batch,/!child\.blocked/);assert.match(batch,/>Aplicar<\/button>/);assert.match(batch,/>Rechazar<\/button>/)})
test('batch proposalRef resolves child payload name without showing UUID',()=>{assert.match(preview,/children\.find\(item=>item\.clientRef===proposalRef\)/);assert.match(preview,/child\.payload\.name/);assert.doesNotMatch(preview,/JSON\.stringify/)})
