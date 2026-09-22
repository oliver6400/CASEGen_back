package com.casegen.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.DiagramEdgeRepository;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.repository.UmlAssociationEndRepository;
import com.casegen.modeling.repository.UmlGeneralizationRepository;
import com.casegen.modeling.repository.UmlRealizationRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.service.ProjectAuthorizationService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CollaborationActivityServiceTest {
    private final CollaborationActivityRepository activities = Mockito.mock(CollaborationActivityRepository.class);
    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final UmlModelRepository models = Mockito.mock(UmlModelRepository.class);
    private final DiagramRepository diagrams = Mockito.mock(DiagramRepository.class);
    private final UmlElementRepository elements = Mockito.mock(UmlElementRepository.class);
    private final DiagramNodeRepository nodes = Mockito.mock(DiagramNodeRepository.class);
    private final DiagramEdgeRepository edges = Mockito.mock(DiagramEdgeRepository.class);
    private final UmlAssociationEndRepository associationEnds = Mockito.mock(UmlAssociationEndRepository.class);
    private final UmlGeneralizationRepository generalizations = Mockito.mock(UmlGeneralizationRepository.class);
    private final UmlRealizationRepository realizations = Mockito.mock(UmlRealizationRepository.class);
    private final ProjectAuthorizationService authorization = Mockito.mock(ProjectAuthorizationService.class);
    private final CollaborationActivityService service = new CollaborationActivityService(
            activities, users, models, diagrams, elements, nodes, edges,
            associationEnds, generalizations, realizations, authorization);

    @Test
    void recordsOnlyConfirmedImportantChangeWithActorAndSubject() {
        UUID projectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        User actor = Mockito.mock(User.class);
        UmlModel model = Mockito.mock(UmlModel.class);
        Project project = Mockito.mock(Project.class);
        UmlElement element = Mockito.mock(UmlElement.class);
        when(actor.getId()).thenReturn(actorId);
        when(actor.getDisplayName()).thenReturn("Oliver");
        when(model.getProject()).thenReturn(project);
        when(element.getName()).thenReturn("price");
        when(users.findById(actorId)).thenReturn(Optional.of(actor));
        when(models.findById(modelId)).thenReturn(Optional.of(model));
        when(elements.findById(propertyId)).thenReturn(Optional.of(element));
        when(activities.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        RealtimeEvent event = new RealtimeEvent(UUID.randomUUID(), RealtimeEventType.PROPERTY_CREATED,
                projectId, modelId, null, propertyId, 12L, null, actorId, Instant.now());

        CollaborationActivityResponse result = service.recordConfirmed(event);

        assertEquals("Oliver agregó price", result.description());
        assertEquals(12L, result.modelRevision());
        ArgumentCaptor<CollaborationActivity> saved = ArgumentCaptor.forClass(CollaborationActivity.class);
        verify(activities).save(saved.capture());
        assertEquals(RealtimeEventType.PROPERTY_CREATED, saved.getValue().getEventType());
    }

    @Test
    void doesNotPersistNonHistoricalEvent() {
        RealtimeEvent event = new RealtimeEvent(UUID.randomUUID(), RealtimeEventType.MODEL_VALIDATION_CHANGED,
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), 1L, null,
                UUID.randomUUID(), Instant.now());

        assertNull(service.recordConfirmed(event));
        verify(activities, never()).save(Mockito.any());
    }
}
