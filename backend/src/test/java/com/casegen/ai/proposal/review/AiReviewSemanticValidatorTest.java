package com.casegen.ai.proposal.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casegen.modeling.domain.*;
import com.casegen.modeling.repository.UmlElementRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiReviewSemanticValidatorTest {
    private final UUID projectId=UUID.randomUUID(),modelId=UUID.randomUUID(),sourceId=UUID.randomUUID();
    private final UUID classA=UUID.randomUUID(),classB=UUID.randomUUID(),interfaceId=UUID.randomUUID();
    private UmlElementRepository elements;
    private AiReviewSemanticValidator validator;

    @BeforeEach void setUp(){elements=mock(UmlElementRepository.class);validator=new AiReviewSemanticValidator(
            new AiReviewPayloadCanonicalizer(JsonMapper.builder().build()),new AiProposalDependencyGraph(),elements);}

    @Test void inventedUuidIsRejected(){var error=assertThrows(AiReviewValidationException.class,()->
            validate(snapshot(List.of()),item("attr",AiReviewProposalType.ADD_ATTRIBUTE,attribute(new ReviewElementRef(UUID.randomUUID(),null),"x")),1));
        assertEquals(AiReviewValidationErrorCode.REFERENCED_ELEMENT_NOT_FOUND,error.getCode());}

    @Test void uuidFromAnotherModelIsRejected(){UUID foreign=UUID.randomUUID();UmlElement element=mock(UmlElement.class);UmlModel model=mock(UmlModel.class);
        when(element.getModel()).thenReturn(model);when(model.getId()).thenReturn(UUID.randomUUID());when(elements.findById(foreign)).thenReturn(Optional.of(element));
        var error=assertThrows(AiReviewValidationException.class,()->validate(snapshot(List.of()),item("attr",AiReviewProposalType.ADD_ATTRIBUTE,attribute(new ReviewElementRef(foreign,null),"x")),1));
        assertEquals(AiReviewValidationErrorCode.ELEMENT_OUTSIDE_MODEL,error.getCode());}

    @Test void existingAndBatchClassifierDuplicatesAreRejectedCaseInsensitively(){AiUmlReviewSnapshot s=snapshot(List.of(classifier(classA,"Producto",ClassifierKind.CLASS)));
        assertCode(AiReviewValidationErrorCode.DUPLICATE_ELEMENT,()->validate(s,item("p",AiReviewProposalType.ADD_CLASSIFIER,classifierPayload("producto",ClassifierKind.CLASS)),1));
        assertCode(AiReviewValidationErrorCode.DUPLICATE_ELEMENT,()->validate(s,List.of(
                item("one",AiReviewProposalType.ADD_CLASSIFIER,classifierPayload("Nuevo",ClassifierKind.CLASS)),
                item("two",AiReviewProposalType.ADD_CLASSIFIER,classifierPayload("NUEVO",ClassifierKind.CLASS))),1));}

    @Test void incompatibleProposalReferenceIsRejected(){AiReviewItem attribute=item("attribute",AiReviewProposalType.ADD_ATTRIBUTE,
                attribute(new ReviewElementRef(null,"other-attribute"),"x"));
        AiReviewItem other=item("other-attribute",AiReviewProposalType.ADD_ATTRIBUTE,
                attribute(new ReviewElementRef(classA,null),"y"));
        assertCode(AiReviewValidationErrorCode.INCOMPATIBLE_REFERENCE,()->validate(snapshot(List.of(classifier(classA,"A",ClassifierKind.CLASS))),List.of(attribute,other),1));}

    @Test void existingGeneralizationCycleIsRejected(){AiUmlReviewSnapshot base=snapshot(List.of(classifier(classA,"A",ClassifierKind.CLASS),classifier(classB,"B",ClassifierKind.CLASS)),
                List.of(new AiUmlReviewSnapshot.GeneralizationEntry(UUID.randomUUID(),classA,classB,true,null,false)));
        AiReviewItem reverse=item("reverse",AiReviewProposalType.ADD_GENERALIZATION,new ReviewAddGeneralizationPayload(
                new ReviewElementRef(classB,null),new ReviewElementRef(classA,null),true,null));
        assertCode(AiReviewValidationErrorCode.DUPLICATE_ELEMENT,()->validate(base,reverse,1));}

    @Test void invalidRealizationAndRevisionChangeAreRejected(){AiUmlReviewSnapshot base=snapshot(List.of(classifier(classA,"A",ClassifierKind.CLASS),classifier(classB,"B",ClassifierKind.CLASS)));
        AiReviewItem invalid=item("real",AiReviewProposalType.ADD_REALIZATION,new ReviewAddRealizationPayload(
                new ReviewElementRef(classA,null),new ReviewElementRef(classB,null),null));
        assertCode(AiReviewValidationErrorCode.INVALID_PAYLOAD,()->validate(base,invalid,1));
        assertCode(AiReviewValidationErrorCode.REVIEW_CONTEXT_CHANGED,()->validate(base,item("new",AiReviewProposalType.ADD_CLASSIFIER,classifierPayload("New",ClassifierKind.CLASS)),0));}

    @Test void duplicateExistingAttributeAndDuplicateUpdateTargetAreRejected(){UUID propertyId=UUID.randomUUID();
        AiUmlReviewSnapshot base=snapshotWithProperties(List.of(classifier(classA,"Producto",ClassifierKind.CLASS)),List.of(
                new AiUmlReviewSnapshot.PropertyEntry(propertyId,classA,"sku",null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.STRING,null,0,1,false,false,false)));
        assertCode(AiReviewValidationErrorCode.DUPLICATE_ELEMENT,()->validate(base,item("sku",AiReviewProposalType.ADD_ATTRIBUTE,attribute(new ReviewElementRef(classA,null),"SKU")),1));
        ReviewUpdateAttributePayload update=new ReviewUpdateAttributePayload(propertyId,"sku",null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.REAL,null,0,1,false,false,false);
        assertCode(AiReviewValidationErrorCode.DUPLICATE_ELEMENT,()->validate(base,List.of(item("u1",AiReviewProposalType.UPDATE_ATTRIBUTE,update),item("u2",AiReviewProposalType.UPDATE_ATTRIBUTE,update)),1));}

    @Test void classifierRenameCollisionAndActiveInterfaceAreRejected(){AiUmlReviewSnapshot base=snapshot(List.of(
                classifier(classA,"Producto",ClassifierKind.CLASS),classifier(classB,"Inventario",ClassifierKind.CLASS),classifier(interfaceId,"Repositorio",ClassifierKind.INTERFACE)));
        assertCode(AiReviewValidationErrorCode.DUPLICATE_ELEMENT,()->validate(base,item("rename",AiReviewProposalType.UPDATE_CLASSIFIER,
                new ReviewUpdateClassifierPayload(classB,"Producto",null,null,Visibility.PUBLIC,false,false)),1));
        assertCode(AiReviewValidationErrorCode.INVALID_PAYLOAD,()->validate(base,item("activate",AiReviewProposalType.UPDATE_CLASSIFIER,
                new ReviewUpdateClassifierPayload(interfaceId,"Repositorio",null,null,Visibility.PUBLIC,false,true)),1));}

    @Test void generalizationCycleMadeOnlyByBatchIsRejected(){UUID classC=UUID.randomUUID();AiUmlReviewSnapshot base=snapshot(List.of(
                classifier(classA,"A",ClassifierKind.CLASS),classifier(classB,"B",ClassifierKind.CLASS),classifier(classC,"C",ClassifierKind.CLASS)));
        List<AiReviewItem> cycle=List.of(generalization("ab",classA,classB),generalization("bc",classB,classC),generalization("ca",classC,classA));
        assertCode(AiReviewValidationErrorCode.DUPLICATE_ELEMENT,()->validate(base,cycle,1));}

    private ValidatedAiReview validate(AiUmlReviewSnapshot snapshot,AiReviewItem item,long revision){return validate(snapshot,List.of(item),revision);}
    private ValidatedAiReview validate(AiUmlReviewSnapshot snapshot,List<AiReviewItem> items,long revision){return validator.validate(new GeneratedAiReview(
            new AiReviewResult("Review","Summary",items,"GEMINI"),revision,null,modelId,null,sourceId),snapshot);}
    private AiReviewItem item(String ref,AiReviewProposalType type,AiReviewPayload payload){return new AiReviewItem(ref,type,ref,"reason",List.of(),payload);}
    private ReviewAddClassifierPayload classifierPayload(String name,ClassifierKind kind){return new ReviewAddClassifierPayload(name,null,null,kind,Visibility.PUBLIC,false,false);}
    private ReviewAddAttributePayload attribute(ReviewElementRef owner,String name){return new ReviewAddAttributePayload(owner,name,null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.STRING,null,0,1,false,false,false);}
    private AiUmlReviewSnapshot.ClassifierEntry classifier(UUID id,String name,ClassifierKind kind){return new AiUmlReviewSnapshot.ClassifierEntry(id,name,null,null,kind,Visibility.PUBLIC,false,false,false);}
    private AiUmlReviewSnapshot snapshot(List<AiUmlReviewSnapshot.ClassifierEntry> classifiers){return snapshot(classifiers,List.of());}
    private AiUmlReviewSnapshot snapshot(List<AiUmlReviewSnapshot.ClassifierEntry> classifiers,List<AiUmlReviewSnapshot.GeneralizationEntry> gens){return new AiUmlReviewSnapshot(projectId,"P",modelId,"M",1,null,false,new AiUmlReviewSnapshot.Counts(0,classifiers.size(),0,0,gens.size()),List.of(),classifiers,List.of(),List.of(),List.of(),gens,List.of());}
    private AiUmlReviewSnapshot snapshotWithProperties(List<AiUmlReviewSnapshot.ClassifierEntry> classifiers,List<AiUmlReviewSnapshot.PropertyEntry> properties){return new AiUmlReviewSnapshot(projectId,"P",modelId,"M",1,null,false,new AiUmlReviewSnapshot.Counts(0,classifiers.size(),properties.size(),0,0),List.of(),classifiers,properties,List.of(),List.of(),List.of(),List.of());}
    private AiReviewItem generalization(String ref,UUID specific,UUID general){return item(ref,AiReviewProposalType.ADD_GENERALIZATION,new ReviewAddGeneralizationPayload(new ReviewElementRef(specific,null),new ReviewElementRef(general,null),true,null));}
    private void assertCode(AiReviewValidationErrorCode code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(AiReviewValidationException.class,action).getCode());}
}
