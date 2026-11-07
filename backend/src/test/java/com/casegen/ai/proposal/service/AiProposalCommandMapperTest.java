package com.casegen.ai.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.payload.AddAssociationPayload;
import com.casegen.ai.proposal.payload.AddAttributePayload;
import com.casegen.ai.proposal.payload.AddClassifierPayload;
import com.casegen.ai.proposal.payload.AddGeneralizationPayload;
import com.casegen.ai.proposal.payload.AddOperationPayload;
import com.casegen.ai.proposal.payload.AddRealizationPayload;
import com.casegen.ai.proposal.payload.AssociationEndProposalPayload;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlAssociationResponse;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlGeneralizationResponse;
import com.casegen.modeling.dto.UmlOperationResponse;
import com.casegen.modeling.dto.UmlPropertyResponse;
import com.casegen.modeling.dto.UmlRealizationResponse;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiProposalCommandMapperTest {
    private final UmlModelService models = mock(UmlModelService.class);
    private final UmlRelationshipService relationships = mock(UmlRelationshipService.class);
    private final UUID modelId = UUID.randomUUID();
    private final UUID elementId = UUID.randomUUID();
    private AiProposalCommandMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AiProposalCommandMapper(models, relationships);
    }

    @Test
    void mapsClassifier() {
        var response = mock(UmlClassifierResponse.class);
        result(response, 11L);
        when(models.createClassifier(any(), any())).thenReturn(response);
        var payload = new AddClassifierPayload(ClassifierKind.CLASS, "Order", null,
                Visibility.PUBLIC, false, true, "doc");
        assertApplied(mapper.apply(modelId, AiProposalType.ADD_CLASSIFIER, payload), 11L);
        verify(models).createClassifier(any(), any());
    }

    @Test
    void mapsAttribute() {
        var response = mock(UmlPropertyResponse.class);
        result(response, 12L);
        when(models.createProperty(any(), any(), any())).thenReturn(response);
        var payload = new AddAttributePayload(UUID.randomUUID(), "total", Visibility.PRIVATE,
                TypeKind.PRIMITIVE, PrimitiveType.REAL, null, 1, 1, false, false, false);
        assertApplied(mapper.apply(modelId, AiProposalType.ADD_ATTRIBUTE, payload), 12L);
        verify(models).createProperty(any(), any(), any());
    }

    @Test
    void mapsOperation() {
        var response = mock(UmlOperationResponse.class);
        result(response, 13L);
        when(models.createOperation(any(), any(), any())).thenReturn(response);
        var payload = new AddOperationPayload(UUID.randomUUID(), "pay", Visibility.PUBLIC,
                false, false, false);
        assertApplied(mapper.apply(modelId, AiProposalType.ADD_OPERATION, payload), 13L);
        verify(models).createOperation(any(), any(), any());
    }

    @Test
    void mapsAssociation() {
        var response = mock(UmlAssociationResponse.class);
        result(response, 14L);
        when(relationships.createAssociation(any(), any())).thenReturn(response);
        var first = end();
        var payload = new AddAssociationPayload("orders", null, first, end());
        assertApplied(mapper.apply(modelId, AiProposalType.ADD_ASSOCIATION, payload), 14L);
        verify(relationships).createAssociation(any(), any());
    }

    @Test
    void mapsGeneralization() {
        var response = mock(UmlGeneralizationResponse.class);
        result(response, 15L);
        when(relationships.createGeneralization(any(), any())).thenReturn(response);
        var payload = new AddGeneralizationPayload(UUID.randomUUID(), UUID.randomUUID(), true, "doc");
        assertApplied(mapper.apply(modelId, AiProposalType.ADD_GENERALIZATION, payload), 15L);
        verify(relationships).createGeneralization(any(), any());
    }

    @Test
    void mapsRealization() {
        var response = mock(UmlRealizationResponse.class);
        result(response, 16L);
        when(relationships.createRealization(any(), any())).thenReturn(response);
        var payload = new AddRealizationPayload(UUID.randomUUID(), UUID.randomUUID(), "doc");
        assertApplied(mapper.apply(modelId, AiProposalType.ADD_REALIZATION, payload), 16L);
        verify(relationships).createRealization(any(), any());
    }

    private AssociationEndProposalPayload end() {
        return new AssociationEndProposalPayload(UUID.randomUUID(), "role", 0, null, true,
                AggregationKind.NONE, AssociationEndOwnership.ASSOCIATION);
    }

    private void assertApplied(AiProposalAppliedElement result, long revision) {
        assertThat(result).isEqualTo(new AiProposalAppliedElement(elementId, revision));
    }

    private void result(Object response, long revision) {
        if (response instanceof UmlClassifierResponse value) {
            when(value.id()).thenReturn(elementId);
            when(value.modelRevision()).thenReturn(revision);
        } else if (response instanceof UmlPropertyResponse value) {
            when(value.id()).thenReturn(elementId);
            when(value.modelRevision()).thenReturn(revision);
        } else if (response instanceof UmlOperationResponse value) {
            when(value.id()).thenReturn(elementId);
            when(value.modelRevision()).thenReturn(revision);
        } else if (response instanceof UmlAssociationResponse value) {
            when(value.id()).thenReturn(elementId);
            when(value.modelRevision()).thenReturn(revision);
        } else if (response instanceof UmlGeneralizationResponse value) {
            when(value.id()).thenReturn(elementId);
            when(value.modelRevision()).thenReturn(revision);
        } else if (response instanceof UmlRealizationResponse value) {
            when(value.id()).thenReturn(elementId);
            when(value.modelRevision()).thenReturn(revision);
        }
    }
}
