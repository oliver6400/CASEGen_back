package com.casegen.ai.proposal.review;

import com.casegen.ai.provider.AiProviderResult;
import com.casegen.modeling.domain.TypeKind;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AiReviewResultParser {
    private static final Logger LOGGER=LoggerFactory.getLogger(AiReviewResultParser.class);
    public static final int MAX_ITEMS=15,MAX_CLASSIFIERS=5,MAX_ATTRIBUTES=8,MAX_RELATIONSHIPS=6;
    private final JsonMapper mapper;
    public AiReviewResultParser(JsonMapper json){mapper=json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();}
    public AiReviewResult parse(AiProviderResult generated){
        JsonNode root=null;String phase="SANITIZE";
        try{
            phase="JSON_PARSE";root=mapper.readTree(sanitize(generated.content()));
            if(root==null||!root.isObject())throw invalid("review must be one JSON object");
            phase="ENVELOPE";
            Envelope envelope=mapper.treeToValue(root,Envelope.class);
            String title=text(envelope.reviewTitle,"reviewTitle",180),summary=text(envelope.summary,"summary",Integer.MAX_VALUE);
            if(envelope.items==null||envelope.items.isEmpty()||envelope.items.size()>MAX_ITEMS)throw invalid("review must contain between 1 and 15 items");
            List<AiReviewItem> items=new ArrayList<>();Set<String> refs=new HashSet<>();int classifiers=0,attributes=0,relationships=0;
            phase="ITEMS";for(RawItem raw:envelope.items){String ref=clientRef(raw.clientRef);if(!refs.add(ref))throw invalid("duplicate clientRef");
                if(raw.proposalType==null||raw.dependencies==null||raw.payload==null||!raw.payload.isObject())throw invalid("review item is incomplete");
                Set<String> deps=new LinkedHashSet<>();for(String dependency:raw.dependencies){String normalized=clientRef(dependency);if(normalized.equals(ref)||!deps.add(normalized))throw invalid("invalid dependency list");}
                AiReviewPayload payload=parsePayload(raw.proposalType,raw.payload);validatePayload(raw.proposalType,payload);
                if(raw.proposalType==AiReviewProposalType.ADD_CLASSIFIER)classifiers++;
                if(raw.proposalType==AiReviewProposalType.ADD_ATTRIBUTE)attributes++;
                if(EnumSet.of(AiReviewProposalType.ADD_ASSOCIATION,AiReviewProposalType.ADD_GENERALIZATION,AiReviewProposalType.ADD_REALIZATION).contains(raw.proposalType))relationships++;
                items.add(new AiReviewItem(ref,raw.proposalType,text(raw.title,"title",180),text(raw.rationale,"rationale",Integer.MAX_VALUE),List.copyOf(deps),payload));}
            if(classifiers>MAX_CLASSIFIERS||attributes>MAX_ATTRIBUTES||relationships>MAX_RELATIONSHIPS)throw invalid("review exceeds per-type limits");
            return new AiReviewResult(title,summary,items,generated.provider());
        }catch(AiReviewGenerationException e){logFailure(generated,phase,root,e);throw e;}catch(RuntimeException e){logFailure(generated,phase,root,e);throw new AiReviewGenerationException("review response does not match the strict JSON contract",e);}
    }
    private AiReviewPayload parsePayload(AiReviewProposalType type,JsonNode payload){return switch(type){
        case ADD_CLASSIFIER->mapper.treeToValue(payload,ReviewAddClassifierPayload.class);
        case ADD_ATTRIBUTE->mapper.treeToValue(payload,ReviewAddAttributePayload.class);
        case ADD_OPERATION->mapper.treeToValue(payload,ReviewAddOperationPayload.class);
        case ADD_ASSOCIATION->mapper.treeToValue(payload,ReviewAddAssociationPayload.class);
        case ADD_GENERALIZATION->mapper.treeToValue(payload,ReviewAddGeneralizationPayload.class);
        case ADD_REALIZATION->mapper.treeToValue(payload,ReviewAddRealizationPayload.class);
        case UPDATE_CLASSIFIER->mapper.treeToValue(payload,ReviewUpdateClassifierPayload.class);
        case UPDATE_ATTRIBUTE->mapper.treeToValue(payload,ReviewUpdateAttributePayload.class);};}
    private void validatePayload(AiReviewProposalType type,AiReviewPayload payload){boolean valid=switch(type){
        case ADD_CLASSIFIER->{var p=(ReviewAddClassifierPayload)payload;yield p.name()!=null&&p.kind()!=null&&p.visibility()!=null&&p.abstractClassifier()!=null&&p.active()!=null;}
        case ADD_ATTRIBUTE->{var p=(ReviewAddAttributePayload)payload;yield p.ownerRef()!=null&&p.name()!=null&&p.visibility()!=null&&typed(p.typeKind(),p.primitiveType(),p.typeClassifierRef())&&multiplicity(p.lowerBound(),p.upperBound())&&p.staticProperty()!=null&&p.readOnly()!=null&&p.derived()!=null;}
        case ADD_OPERATION->{var p=(ReviewAddOperationPayload)payload;yield p.ownerRef()!=null&&p.name()!=null&&p.visibility()!=null&&p.abstractOperation()!=null&&p.staticOperation()!=null&&p.query()!=null;}
        case ADD_ASSOCIATION->{var p=(ReviewAddAssociationPayload)payload;yield end(p.firstEnd())&&end(p.secondEnd());}
        case ADD_GENERALIZATION->{var p=(ReviewAddGeneralizationPayload)payload;yield p.specificRef()!=null&&p.generalRef()!=null&&p.substitutable()!=null;}
        case ADD_REALIZATION->{var p=(ReviewAddRealizationPayload)payload;yield p.clientClassifierRef()!=null&&p.supplierRef()!=null;}
        case UPDATE_CLASSIFIER->{var p=(ReviewUpdateClassifierPayload)payload;yield p.targetClassifierId()!=null&&p.name()!=null&&p.visibility()!=null&&p.abstractClassifier()!=null&&p.active()!=null;}
        case UPDATE_ATTRIBUTE->{var p=(ReviewUpdateAttributePayload)payload;yield p.targetPropertyId()!=null&&p.name()!=null&&p.visibility()!=null&&typed(p.typeKind(),p.primitiveType(),p.typeClassifierRef())&&multiplicity(p.lowerBound(),p.upperBound())&&p.staticProperty()!=null&&p.readOnly()!=null&&p.derived()!=null;}};
        if(!valid)throw invalid("review payload is missing or has incompatible fields");}
    private boolean typed(TypeKind kind,Object primitive,ReviewElementRef classifier){return kind==TypeKind.PRIMITIVE&&primitive!=null&&classifier==null||kind==TypeKind.CLASSIFIER&&primitive==null&&classifier!=null;}
    private boolean multiplicity(Integer lower,Integer upper){return lower!=null&&lower>=0&&(upper==null||upper>=lower);}
    private boolean end(ReviewAssociationEndPayload e){return e!=null&&e.classifierRef()!=null&&multiplicity(e.lowerBound(),e.upperBound())&&e.navigable()!=null&&e.aggregation()!=null&&e.ownership()!=null;}
    private String sanitize(String raw){if(raw==null||raw.isBlank())throw invalid("review response is empty");String value=raw.trim();if(value.startsWith("```json")&&value.endsWith("```"))return value.substring(7,value.length()-3).trim();if(value.startsWith("```")||value.endsWith("```"))throw invalid("only a complete json code fence is accepted");return value;}
    private String clientRef(String value){String ref=text(value,"clientRef",80);if(!ref.matches("[a-z0-9][a-z0-9-]*"))throw invalid("clientRef has an invalid format");return ref;}
    private String text(String value,String field,int max){if(value==null||value.isBlank())throw invalid(field+" must not be blank");String n=value.trim();if(n.length()>max)throw invalid(field+" is too long");return n;}
    private AiReviewGenerationException invalid(String message){return new AiReviewGenerationException(message);}
    private void logFailure(AiProviderResult generated,String phase,JsonNode root,RuntimeException error){
        List<String> fields=new ArrayList<>();Map<String,Long> types=new TreeMap<>();int count=-1;
        if(root!=null&&root.isObject())root.propertyStream().forEach(entry->fields.add(entry.getKey()));
        JsonNode items=root==null?null:root.get("items");if(items!=null&&items.isArray()){count=items.size();for(JsonNode item:items){JsonNode type=item.get("proposalType");String key=type!=null&&type.isTextual()?type.asText():"<missing>";types.merge(key,1L,Long::sum);}}
        Throwable concrete=error.getCause()==null?error:error.getCause();
        LOGGER.warn("AI review rejected errorCode={} provider={} parserPhase={} itemCount={} proposalTypes={} rootFields={} exception={} reason={}",
                diagnosticCode(error),generated==null?"<unknown>":generated.provider(),phase,count,types,fields,
                concrete.getClass().getSimpleName(),safeReason(error));
    }
    private String diagnosticCode(RuntimeException error){
        String message=Optional.ofNullable(error.getMessage()).orElse("");String type=(error.getCause()==null?error:error.getCause()).getClass().getSimpleName();
        if(message.contains("between 1 and 15"))return "ITEM_COUNT_LIMIT";
        if(message.contains("per-type limits"))return "PROPOSAL_TYPE_LIMIT";
        if(message.contains("dependency"))return "INVALID_DEPENDENCY";
        if(message.contains("clientRef")||message.contains("element reference"))return "INVALID_REFERENCE";
        if(message.contains("payload"))return "INCOMPATIBLE_PAYLOAD";
        if(type.contains("UnrecognizedProperty"))return "UNKNOWN_FIELD";
        if(type.contains("InvalidFormat")||type.contains("ValueInstantiation"))return "INVALID_ENUM_OR_VALUE";
        if(type.contains("Json")||type.contains("StreamRead"))return "INVALID_JSON";
        return "STRICT_CONTRACT_MISMATCH";
    }
    private String safeReason(RuntimeException error){
        String message=Optional.ofNullable(error.getMessage()).orElse("unspecified parser error");
        int lineBreak=message.indexOf('\n');return lineBreak<0?message:message.substring(0,lineBreak);
    }
    private record Envelope(String reviewTitle,String summary,List<RawItem> items){}
    private record RawItem(String clientRef,AiReviewProposalType proposalType,String title,String rationale,List<String> dependencies,JsonNode payload){}
}
