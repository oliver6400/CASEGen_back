package com.casegen.ai.proposal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.proposal.context.AiProposalContext;
import com.casegen.ai.proposal.context.AiProposalContextBuilder;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.payload.AddClassifierPayload;
import com.casegen.ai.proposal.payload.AddGeneralizationPayload;
import com.casegen.ai.proposal.payload.AiProposalPayloadParser;
import com.casegen.ai.proposal.repository.AiProposalRepository;
import com.casegen.ai.proposal.realtime.AiProposalEventPublisher;
import com.casegen.ai.proposal.validation.AiProposalPayloadValidator;
import com.casegen.ai.proposal.validation.AiProposalValidationErrorCode;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.service.DiagramService;
import com.casegen.project.domain.Project;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.node.JsonNodeFactory;

class AiProposalDecisionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-30T20:00:00Z");
    private final ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
    private final AiProposalRepository proposals = mock(AiProposalRepository.class);
    private final UmlModelRepository models = mock(UmlModelRepository.class);
    private final AiProposalPayloadParser parser = mock(AiProposalPayloadParser.class);
    private final AiProposalContextBuilder contexts = mock(AiProposalContextBuilder.class);
    private final AiProposalPayloadValidator validator = mock(AiProposalPayloadValidator.class);
    private final AiProposalCommandMapper mapper = mock(AiProposalCommandMapper.class);
    private final DiagramService diagrams = mock(DiagramService.class);
    private final AiProposalEventPublisher events = mock(AiProposalEventPublisher.class);
    private final UUID projectId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID modelId = UUID.randomUUID();
    private final User actor = mock(User.class);
    private final AiProposal proposal = mock(AiProposal.class);
    private final UmlModel model = mock(UmlModel.class);
    private AiProposalDecisionService service;

    @BeforeEach
    void setUp() {
        service = new AiProposalDecisionService(authorization, proposals, models, parser,
                contexts, validator, mapper, diagrams, events, Clock.fixed(NOW, ZoneOffset.UTC));
        Project project = mock(Project.class);
        when(actor.getId()).thenReturn(actorId);
        when(authorization.requireWrite(projectId)).thenReturn(actor);
        when(proposals.findForDecision(projectId, proposalId)).thenReturn(Optional.of(proposal));
        when(proposal.getStatus()).thenReturn(AiProposalStatus.PENDING);
        when(proposal.getId()).thenReturn(proposalId);
        when(proposal.getModel()).thenReturn(model);
        when(proposal.getProposalType()).thenReturn(AiProposalType.ADD_CLASSIFIER);
        when(proposal.getPayload()).thenReturn(JsonNodeFactory.instance.objectNode());
        when(proposal.getBaseModelRevision()).thenReturn(4L);
        when(model.getId()).thenReturn(modelId);
        when(model.getRevision()).thenReturn(4L);
        when(model.getProject()).thenReturn(project);
        when(project.getId()).thenReturn(projectId);
        when(models.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
    }

    @Test
    void applyLocksRevalidatesCreatesAndRecordsServerResultInOrder() {
        var payload = mock(AddClassifierPayload.class);
        var context = mock(AiProposalContext.class);
        UUID elementId = UUID.randomUUID();
        when(parser.parse(any(), any())).thenReturn(payload);
        when(contexts.build(projectId, modelId, null)).thenReturn(context);
        when(validator.validate(any(), any(), any())).thenReturn(payload);
        when(mapper.apply(modelId, AiProposalType.ADD_CLASSIFIER, payload))
                .thenReturn(new AiProposalAppliedElement(elementId, 5L));
        when(proposals.saveAndFlush(proposal)).thenReturn(proposal);

        service.apply(command(" approved "));

        verify(proposal).applyDecision(actor, NOW, " approved ", 5L, elementId);
        verify(events).applied(projectId, proposalId, NOW);
        InOrder order = inOrder(authorization, proposals, models, parser, contexts, validator,
                mapper);
        order.verify(authorization).requireWrite(projectId);
        order.verify(proposals).findForDecision(projectId, proposalId);
        order.verify(models).findByIdForUpdate(modelId);
        order.verify(parser).parse(any(), any());
        order.verify(contexts).build(projectId, modelId, null);
        order.verify(validator).validate(any(), any(), any());
        order.verify(mapper).apply(modelId, AiProposalType.ADD_CLASSIFIER, payload);
        verify(diagrams, never()).addClassifierNodeAutoPlaced(any(), any());
    }

    @Test
    void classifierWithDiagramUsesProposalDiagramForVisualRepresentation() {
        var payload = mock(AddClassifierPayload.class);
        var context = mock(AiProposalContext.class);
        var proposalDiagram = mock(Diagram.class);
        UUID diagramId = UUID.randomUUID();
        UUID elementId = UUID.randomUUID();
        when(proposal.getDiagram()).thenReturn(proposalDiagram);
        when(proposalDiagram.getId()).thenReturn(diagramId);
        when(parser.parse(any(), any())).thenReturn(payload);
        when(contexts.build(projectId, modelId, diagramId)).thenReturn(context);
        when(validator.validate(any(), any(), any())).thenReturn(payload);
        when(mapper.apply(modelId, AiProposalType.ADD_CLASSIFIER, payload))
                .thenReturn(new AiProposalAppliedElement(elementId, 5L));
        when(proposals.saveAndFlush(proposal)).thenReturn(proposal);

        service.apply(command(null));

        verify(diagrams).addClassifierNodeAutoPlaced(diagramId, elementId);
    }

    @Test
    void relationshipWithDiagramUsesProposalDiagramForVisualRepresentation() {
        var payload = mock(AddGeneralizationPayload.class);
        var context = mock(AiProposalContext.class);
        var proposalDiagram = mock(Diagram.class);
        UUID diagramId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        when(proposal.getProposalType()).thenReturn(AiProposalType.ADD_GENERALIZATION);
        when(proposal.getDiagram()).thenReturn(proposalDiagram);
        when(proposalDiagram.getId()).thenReturn(diagramId);
        when(parser.parse(any(), any())).thenReturn(payload);
        when(contexts.build(projectId, modelId, diagramId)).thenReturn(context);
        when(validator.validate(any(), any(), any())).thenReturn(payload);
        when(mapper.apply(modelId, AiProposalType.ADD_GENERALIZATION, payload))
                .thenReturn(new AiProposalAppliedElement(relationshipId, 5L));
        when(proposals.saveAndFlush(proposal)).thenReturn(proposal);

        service.apply(command(null));

        verify(diagrams).addRelationshipEdgeIfPossible(diagramId, relationshipId);
        verify(diagrams, never()).addClassifierNodeAutoPlaced(any(), any());
    }

    @Test
    void rejectOnlyLocksProposalAndRecordsDecision() {
        when(proposals.saveAndFlush(proposal)).thenReturn(proposal);
        service.reject(command("no"));
        verify(proposal).rejectDecision(actor, NOW, "no");
        verify(events).rejected(projectId, proposalId, NOW);
        verify(models, never()).findByIdForUpdate(any());
        verify(parser, never()).parse(any(), any());
        verify(mapper, never()).apply(any(), any(), any());
    }

    @Test
    void staleProposalRemainsUndecidedAndDoesNotCreateUml() {
        when(model.getRevision()).thenReturn(5L);
        assertCode(() -> service.apply(command(null)), AiProposalErrorCode.PROPOSAL_STALE);
        verify(mapper, never()).apply(any(), any(), any());
        verify(proposal, never()).applyDecision(any(), any(), any(), anyLong(), any());
    }

    @Test
    void invalidCurrentContextBecomesNoLongerValidBeforeUmlMutation() {
        when(parser.parse(any(), any())).thenThrow(new AiProposalValidationException(
                AiProposalValidationErrorCode.DUPLICATE_ELEMENT, "duplicate"));
        assertCode(() -> service.apply(command(null)),
                AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID);
        verify(mapper, never()).apply(any(), any(), any());
    }

    @Test
    void terminalProposalCannotBeAppliedOrRejected() {
        when(proposal.getStatus()).thenReturn(AiProposalStatus.APPLIED);
        assertCode(() -> service.apply(command(null)),
                AiProposalErrorCode.PROPOSAL_ALREADY_DECIDED);
        assertCode(() -> service.reject(command(null)),
                AiProposalErrorCode.PROPOSAL_ALREADY_DECIDED);
    }

    @Test
    void mapperFailureDoesNotMarkProposalApplied() {
        var payload = mock(AddClassifierPayload.class);
        when(parser.parse(any(), any())).thenReturn(payload);
        when(validator.validate(any(), any(), any())).thenReturn(payload);
        when(mapper.apply(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> service.apply(command(null)))
                .isInstanceOf(IllegalStateException.class);
        verify(proposal, never()).applyDecision(any(), any(), any(), anyLong(), any());
        verify(proposals, never()).saveAndFlush(any());
        verify(events, never()).applied(any(), any(), any());
    }

    @Test
    void rejectsActorDifferentFromAuthenticatedUser() {
        var command = new AiProposalDecisionCommand(projectId, proposalId, UUID.randomUUID(), null);
        assertCode(() -> service.apply(command), AiProposalErrorCode.INVALID_REQUEST);
        verify(proposals, never()).findForDecision(any(), any());
    }

    private AiProposalDecisionCommand command(String note) {
        return new AiProposalDecisionCommand(projectId, proposalId, actorId, note);
    }

    private void assertCode(Runnable operation, AiProposalErrorCode code) {
        assertThatThrownBy(operation::run).isInstanceOf(AiProposalException.class)
                .extracting(error -> ((AiProposalException) error).getCode()).isEqualTo(code);
    }
}
