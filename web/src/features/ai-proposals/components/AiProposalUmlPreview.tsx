import type { AiProposalType } from '../types/aiProposal'
import type { AiProposalBatchChild } from '../types/aiProposalBatch'

type Payload = Record<string, unknown>
type PreviewProps = { proposalType: AiProposalType; payload: Payload; title: string; batchChildren?: AiProposalBatchChild[] }

export function AiProposalUmlPreview({proposalType,payload,title,batchChildren=[]}:PreviewProps){
  switch(proposalType){
    case 'ADD_CLASSIFIER': return <ClassifierPreview payload={payload}/>
    case 'ADD_ATTRIBUTE': return <AttributePreview payload={payload} title={title} children={batchChildren}/>
    case 'UPDATE_ATTRIBUTE': return <UpdateAttributePreview payload={payload} title={title}/>
    case 'ADD_OPERATION': return <OperationPreview payload={payload} title={title} children={batchChildren}/>
    case 'ADD_ASSOCIATION': return <RelationshipPreview payload={payload} title={title} children={batchChildren} kind="association"/>
    case 'ADD_GENERALIZATION': return <RelationshipPreview payload={payload} title={title} children={batchChildren} kind="generalization"/>
    case 'ADD_REALIZATION': return <RelationshipPreview payload={payload} title={title} children={batchChildren} kind="realization"/>
    case 'UPDATE_CLASSIFIER': return <UpdateClassifierPreview payload={payload} title={title}/>
    default: return <PreviewFallback title={title}/>
  }
}

function ClassifierPreview({payload}:{payload:Payload}){
  const name=text(payload.name);if(!name)return <PreviewFallback title="Clase o interfaz"/>
  return <UmlBox name={name} stereotype={text(payload.kind)==='INTERFACE'?'interface':undefined}/>
}
function AttributePreview({payload,title,children}:{payload:Payload;title:string;children:AiProposalBatchChild[]}){
  const name=text(payload.name);if(!name)return <PreviewFallback title={title}/>
  return <UmlBox name={ownerName(payload.ownerRef,payload,title,children)} attributes={[`${visibility(text(payload.visibility))}${name} : ${typeName(payload)} ${multiplicity(payload)}`.trim()]}/>
}
function OperationPreview({payload,title,children}:{payload:Payload;title:string;children:AiProposalBatchChild[]}){
  const name=text(payload.name);if(!name)return <PreviewFallback title={title}/>
  return <UmlBox name={ownerName(payload.ownerRef,payload,title,children)} operations={[`${visibility(text(payload.visibility))}${name}()`]}/>
}
function UpdateAttributePreview({payload,title}:{payload:Payload;title:string}){
  const name=text(payload.name)??'atributo',oldName=text(payload.previousName)??text(payload.oldName)??name
  const next=`${visibility(text(payload.visibility))}${name} : ${typeName(payload)} ${multiplicity(payload)}`.trim()
  const before=`${visibility(text(payload.previousVisibility))}${oldName} : ${text(payload.previousPrimitiveType)??text(payload.oldType)??'tipo actual'}`
  return <UmlBox name={text(payload.ownerName)??text(payload.classifierName)??title} attributes={[`− ${before}`,`+ ${next}`]}/>
}
function UpdateClassifierPreview({payload,title}:{payload:Payload;title:string}){
  const next=text(payload.name)??title,previous=text(payload.previousName)??text(payload.oldName)
  if(previous&&previous!==next)return <div className="uml-preview uml-rename"><span>{previous}</span><span aria-hidden>→</span><strong>{next}</strong></div>
  const changes=[text(payload.visibility)&&`visibility: ${text(payload.visibility)}`,typeof payload.abstractClassifier==='boolean'&&`abstract: ${payload.abstractClassifier}`,typeof payload.active==='boolean'&&`active: ${payload.active}`].filter(Boolean) as string[]
  return <UmlBox name={next} attributes={changes}/>
}
function RelationshipPreview({payload,title,children,kind}:{payload:Payload;title:string;children:AiProposalBatchChild[];kind:'association'|'generalization'|'realization'}){
  let leftRef:unknown,rightRef:unknown,leftEnd:Payload={},rightEnd:Payload={}
  if(kind==='association'){leftEnd=object(payload.firstEnd);rightEnd=object(payload.secondEnd);leftRef=leftEnd.classifierRef;rightRef=rightEnd.classifierRef}
  else if(kind==='generalization'){leftRef=payload.specificRef;rightRef=payload.generalRef}
  else{leftRef=payload.clientClassifierRef;rightRef=payload.supplierRef}
  const left=referenceName(leftRef,payload,['sourceName','specificName','clientName'],title,children)
  const right=referenceName(rightRef,payload,['targetName','generalName','supplierName','interfaceName'],'Elemento relacionado',children)
  const aggregation=kind==='association'?associationAggregation(leftEnd,rightEnd):'NONE'
  return <div className="uml-preview uml-relationship">
    <div><MiniClassifier name={left}/><span>{multiplicity(leftEnd)}</span></div>
    <svg aria-label={kind==='association'?aggregation==='COMPOSITE'?'Composición':aggregation==='SHARED'?'Agregación':'Asociación':kind==='generalization'?'Generalización':'Realización'} viewBox="0 0 120 28" role="img">
      {aggregation==='COMPOSITE'&&<polygon points="8,14 18,6 28,14 18,22" fill="currentColor"/>}
      {aggregation==='SHARED'&&<polygon points="8,14 18,6 28,14 18,22" fill="white" stroke="currentColor" strokeWidth="2"/>}
      <line x1={aggregation==='NONE'?4:28} y1="14" x2={kind==='association'?116:104} y2="14" stroke="currentColor" strokeWidth="2" strokeDasharray={kind==='realization'?'6 5':undefined}/>
      {kind!=='association'&&<polygon points="104,6 116,14 104,22" fill="white" stroke="currentColor" strokeWidth="2"/>}
    </svg>
    <div><MiniClassifier name={kind==='realization'?`«interface» ${right}`:right}/><span>{multiplicity(rightEnd)}</span></div>
    {(text(leftEnd.roleName)||text(rightEnd.roleName))&&<small>{[text(leftEnd.roleName),text(rightEnd.roleName)].filter(Boolean).join(' · ')}</small>}
  </div>
}
function UmlBox({name,stereotype,attributes=[],operations=[]}:{name:string;stereotype?:string;attributes?:string[];operations?:string[]}){return <div className="uml-preview uml-box"><header>{stereotype&&<small>«{stereotype}»</small>}<strong>{name}</strong></header><section>{attributes.map((item,index)=><span key={`${item}-${index}`}>{item}</span>)}</section><section>{operations.map(item=><span key={item}>{item}</span>)}</section></div>}
function MiniClassifier({name}:{name:string}){return <div className="uml-mini-classifier">{name}</div>}
function PreviewFallback({title}:{title:string}){return <div className="uml-preview uml-fallback">{title}</div>}
function ownerName(ref:unknown,payload:Payload,title:string,children:AiProposalBatchChild[]){return referenceName(ref,payload,['ownerName','classifierName'],title,children)}
function referenceName(ref:unknown,payload:Payload,keys:string[],fallback:string,children:AiProposalBatchChild[]){for(const key of keys){const value=text(payload[key]);if(value)return value}const proposalRef=text(object(ref).proposalRef);if(proposalRef){const child=children.find(item=>item.clientRef===proposalRef);if(child)return text(child.payload.name)??child.title??child.clientRef}return fallback}
function associationAggregation(first:Payload,second:Payload){const values=[text(first.aggregation),text(second.aggregation)];return values.includes('COMPOSITE')?'COMPOSITE':values.includes('SHARED')?'SHARED':'NONE'}
function multiplicity(payload:Payload){const lower=number(payload.lowerBound),upper=payload.upperBound===null?'*':number(payload.upperBound);if(lower===undefined&&upper===undefined)return '';return `[${lower??0}..${upper??'*'}]`}
function typeName(payload:Payload){return text(payload.primitiveType)??text(payload.typeName)??text(payload.type)??(payload.typeClassifierRef?'Classifier':'Tipo')}
function visibility(value?:string){return ({PUBLIC:'+ ',PRIVATE:'− ',PROTECTED:'# ',PACKAGE:'~ '} as Record<string,string>)[value??'']??'+ '}
function object(value:unknown):Payload{return value&&typeof value==='object'&&!Array.isArray(value)?value as Payload:{}}
function text(value:unknown){return typeof value==='string'&&value.trim()?value.trim():undefined}
function number(value:unknown){return typeof value==='number'?value:undefined}
