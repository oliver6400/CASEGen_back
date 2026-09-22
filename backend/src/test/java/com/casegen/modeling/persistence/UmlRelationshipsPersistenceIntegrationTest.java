package com.casegen.modeling.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlGeneralizationCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlRealizationCommand;
import com.casegen.modeling.command.UmlAssociationEndCommand;
import com.casegen.modeling.command.UpdateUmlAssociationCommand;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlAssociationResponse;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlAssociationEndRepository;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.DiagramRepresentationCleanupService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UmlModelService.class, UmlRelationshipService.class,
        DiagramRepresentationCleanupService.class,
        UmlRelationshipsPersistenceIntegrationTest.AuthorizationTestConfig.class})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UmlRelationshipsPersistenceIntegrationTest {

    @TestConfiguration
    static class AuthorizationTestConfig {

        @Bean
        ProjectAuthorizationService projectAuthorizationService() {
            return org.mockito.Mockito.mock(ProjectAuthorizationService.class);
        }

        @Bean
        com.casegen.collaboration.RealtimeEventPublisher realtimeEventPublisher() {
            return org.mockito.Mockito.mock(com.casegen.collaboration.RealtimeEventPublisher.class);
        }
    }

    private final UmlModelService modelService;
    private final UmlRelationshipService relationshipService;
    private final ProjectRepository projectRepository;
    private final UmlElementRepository elementRepository;
    private final UmlAssociationEndRepository associationEndRepository;
    private final JdbcTemplate jdbcTemplate;

    UmlRelationshipsPersistenceIntegrationTest(
            UmlModelService modelService,
            UmlRelationshipService relationshipService,
            ProjectRepository projectRepository,
            UmlElementRepository elementRepository,
            UmlAssociationEndRepository associationEndRepository,
            JdbcTemplate jdbcTemplate) {
        this.modelService = modelService;
        this.relationshipService = relationshipService;
        this.projectRepository = projectRepository;
        this.elementRepository = elementRepository;
        this.associationEndRepository = associationEndRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationV6IsApplied() {
        Integer applied = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE success = true AND version = '6'
                """, Integer.class);
        assertEquals(1, applied);
    }

    @Test
    void createsAtomicOneToManyAssociationAndIncrementsRevisionOnce() {
        Fixture fixture = fixture();
        long revisionBefore = modelService.getModel(fixture.model().id()).revision();
        long endsBefore = associationEndRepository.count();

        UmlAssociationResponse association = relationshipService.createAssociation(
                fixture.model().id(), association(
                        fixture.customer().id(), fixture.order().id(), AggregationKind.NONE));

        assertEquals(2, association.ends().size());
        assertEquals(1, association.ends().get(0).lowerBound());
        assertEquals(1, association.ends().get(0).upperBound());
        assertEquals(0, association.ends().get(1).lowerBound());
        assertNull(association.ends().get(1).upperBound());
        assertEquals(revisionBefore + 1, association.modelRevision());
        assertEquals(endsBefore + 2, associationEndRepository.count());
    }

    @Test
    void supportsSharedAggregation() {
        Fixture fixture = fixture();
        UmlAssociationResponse association = relationshipService.createAssociation(
                fixture.model().id(), association(
                        fixture.customer().id(), fixture.order().id(), AggregationKind.SHARED));

        assertEquals(AggregationKind.SHARED, association.ends().get(0).aggregation());
    }

    @Test
    void supportsCompositeAggregation() {
        Fixture fixture = fixture();
        UmlAssociationResponse association = relationshipService.createAssociation(
                fixture.model().id(), association(
                        fixture.customer().id(), fixture.order().id(), AggregationKind.COMPOSITE));

        assertEquals(AggregationKind.COMPOSITE, association.ends().get(0).aggregation());
    }

    @Test
    void rejectsCompositeWholeWithUpperBoundGreaterThanOneAtomically() {
        Fixture fixture = fixture();
        long revisionBefore = modelService.getModel(fixture.model().id()).revision();
        long elementsBefore = elementRepository.count();
        long endsBefore = associationEndRepository.count();
        UmlAssociationEndCommand invalidWhole = end(
                "orders", fixture.customer().id(), 1, 2, AggregationKind.COMPOSITE);
        UmlAssociationEndCommand part = end(
                "items", fixture.order().id(), 1, null, AggregationKind.NONE);

        SemanticException exception = assertThrows(SemanticException.class,
                () -> relationshipService.createAssociation(fixture.model().id(),
                        new CreateUmlAssociationCommand(
                                null, null, null, invalidWhole, part)));

        assertEquals(SemanticErrorCode.INVALID_ASSOCIATION_END, exception.getCode());
        assertEquals(revisionBefore, modelService.getModel(fixture.model().id()).revision());
        assertEquals(elementsBefore, elementRepository.count());
        assertEquals(endsBefore, associationEndRepository.count());
    }

    @Test
    void supportsSelfAssociation() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse employee = persistClassifier(model, "Employee", ClassifierKind.CLASS);

        UmlAssociationResponse association = relationshipService.createAssociation(
                model.id(), association(employee.id(), employee.id(), AggregationKind.NONE));

        assertTrue(association.ends().stream()
                .allMatch(end -> end.classifierId().equals(employee.id())));
    }

    @Test
    void createsValidGeneralization() {
        Fixture fixture = fixture();
        long revisionBefore = modelService.getModel(fixture.model().id()).revision();

        var relation = relationshipService.createGeneralization(fixture.model().id(),
                new CreateUmlGeneralizationCommand(
                        fixture.order().id(), fixture.customer().id(), true, null));

        assertEquals(fixture.order().id(), relation.specificClassifierId());
        assertEquals(fixture.customer().id(), relation.generalClassifierId());
        assertEquals(revisionBefore + 1, relation.modelRevision());
    }

    @Test
    void createsValidInterfaceGeneralization() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse specific = persistClassifier(model, "DetailedPort", ClassifierKind.INTERFACE);
        UmlClassifierResponse general = persistClassifier(model, "Port", ClassifierKind.INTERFACE);

        var relation = relationshipService.createGeneralization(model.id(),
                new CreateUmlGeneralizationCommand(specific.id(), general.id(), true, null));

        assertEquals(specific.id(), relation.specificClassifierId());
        assertEquals(general.id(), relation.generalClassifierId());
    }

    @Test
    void rejectsGeneralizationBetweenClassAndInterfaceAtomically() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse modelClass = persistClassifier(model, "Customer", ClassifierKind.CLASS);
        UmlClassifierResponse modelInterface = persistClassifier(
                model, "Identifiable", ClassifierKind.INTERFACE);
        long revisionBefore = modelService.getModel(model.id()).revision();
        long elementsBefore = elementRepository.count();

        SemanticException exception = assertThrows(SemanticException.class,
                () -> relationshipService.createGeneralization(model.id(),
                        new CreateUmlGeneralizationCommand(
                                modelClass.id(), modelInterface.id(), true, null)));

        assertEquals(SemanticErrorCode.GENERALIZATION_KIND_MISMATCH, exception.getCode());
        assertEquals(revisionBefore, modelService.getModel(model.id()).revision());
        assertEquals(elementsBefore, elementRepository.count());
    }

    @Test
    void rejectsGeneralizationCycleWithoutChangingRevisionOrElements() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse first = persistClassifier(model, "First", ClassifierKind.CLASS);
        UmlClassifierResponse second = persistClassifier(model, "Second", ClassifierKind.CLASS);
        UmlClassifierResponse third = persistClassifier(model, "Third", ClassifierKind.CLASS);
        relationshipService.createGeneralization(model.id(),
                new CreateUmlGeneralizationCommand(first.id(), second.id(), true, null));
        relationshipService.createGeneralization(model.id(),
                new CreateUmlGeneralizationCommand(second.id(), third.id(), true, null));
        long revisionBefore = modelService.getModel(model.id()).revision();
        long elementsBefore = elementRepository.count();

        SemanticException exception = assertThrows(SemanticException.class,
                () -> relationshipService.createGeneralization(model.id(),
                        new CreateUmlGeneralizationCommand(third.id(), first.id(), true, null)));

        assertEquals(SemanticErrorCode.GENERALIZATION_CYCLE, exception.getCode());
        assertEquals(revisionBefore, modelService.getModel(model.id()).revision());
        assertEquals(elementsBefore, elementRepository.count());
    }

    @Test
    void createsRealizationToInterface() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse client = persistClassifier(model, "Service", ClassifierKind.CLASS);
        UmlClassifierResponse supplier = persistClassifier(model, "Port", ClassifierKind.INTERFACE);

        var relation = relationshipService.createRealization(model.id(),
                new CreateUmlRealizationCommand(client.id(), supplier.id(), null));

        assertEquals(client.id(), relation.clientClassifierId());
        assertEquals(supplier.id(), relation.supplierClassifierId());
    }

    @Test
    void rejectsInterfaceAsRealizationClientAtomically() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse client = persistClassifier(model, "ChildPort", ClassifierKind.INTERFACE);
        UmlClassifierResponse supplier = persistClassifier(model, "Port", ClassifierKind.INTERFACE);
        long revisionBefore = modelService.getModel(model.id()).revision();
        long elementsBefore = elementRepository.count();

        SemanticException exception = assertThrows(SemanticException.class,
                () -> relationshipService.createRealization(model.id(),
                        new CreateUmlRealizationCommand(client.id(), supplier.id(), null)));

        assertEquals(SemanticErrorCode.REALIZATION_CLIENT_NOT_CLASS, exception.getCode());
        assertEquals(revisionBefore, modelService.getModel(model.id()).revision());
        assertEquals(elementsBefore, elementRepository.count());
    }

    @Test
    void rejectsRealizationTowardClassAtomically() {
        Fixture fixture = fixture();
        long revisionBefore = modelService.getModel(fixture.model().id()).revision();
        long elementsBefore = elementRepository.count();

        SemanticException exception = assertThrows(SemanticException.class,
                () -> relationshipService.createRealization(fixture.model().id(),
                        new CreateUmlRealizationCommand(
                                fixture.customer().id(), fixture.order().id(), null)));

        assertEquals(SemanticErrorCode.REALIZATION_SUPPLIER_NOT_INTERFACE, exception.getCode());
        assertEquals(revisionBefore, modelService.getModel(fixture.model().id()).revision());
        assertEquals(elementsBefore, elementRepository.count());
    }

    @Test
    void rejectsCrossModelAssociationAtomically() {
        UmlModelResponse firstModel = persistModel();
        UmlModelResponse secondModel = persistModel();
        UmlClassifierResponse first = persistClassifier(firstModel, "First", ClassifierKind.CLASS);
        UmlClassifierResponse second = persistClassifier(secondModel, "Second", ClassifierKind.CLASS);
        long revisionBefore = modelService.getModel(firstModel.id()).revision();
        long elementsBefore = elementRepository.count();

        SemanticException exception = assertThrows(SemanticException.class,
                () -> relationshipService.createAssociation(firstModel.id(),
                        association(first.id(), second.id(), AggregationKind.NONE)));

        assertEquals(SemanticErrorCode.RELATIONSHIP_MODEL_MISMATCH, exception.getCode());
        assertEquals(revisionBefore, modelService.getModel(firstModel.id()).revision());
        assertEquals(elementsBefore, elementRepository.count());
    }

    @Test
    void updatesAndDeletesAssociationWithOneRevisionPerCommand() {
        Fixture fixture = fixture();
        UmlAssociationResponse created = relationshipService.createAssociation(
                fixture.model().id(), association(
                        fixture.customer().id(), fixture.order().id(), AggregationKind.NONE));
        long afterCreate = created.modelRevision();
        CreateUmlAssociationCommand replacement = association(
                fixture.customer().id(), fixture.order().id(), AggregationKind.COMPOSITE);

        UmlAssociationResponse updated = relationshipService.updateAssociation(
                fixture.model().id(), created.id(), new UpdateUmlAssociationCommand(
                        "contains", replacement.documentation(), replacement.packageId(),
                        replacement.firstEnd(), replacement.secondEnd()));
        relationshipService.deleteAssociation(fixture.model().id(), created.id());

        assertEquals(afterCreate + 1, updated.modelRevision());
        assertEquals(afterCreate + 2, modelService.getModel(fixture.model().id()).revision());
        assertTrue(relationshipService.listAssociations(fixture.model().id()).isEmpty());
    }

    @Test
    void rollsBackIncompleteAssociationOnInvalidSecondEnd() {
        Fixture fixture = fixture();
        long revisionBefore = modelService.getModel(fixture.model().id()).revision();
        long elementsBefore = elementRepository.count();
        long endsBefore = associationEndRepository.count();
        UmlAssociationEndCommand first = end(
                "customer", fixture.customer().id(), 1, 1, AggregationKind.NONE);
        UmlAssociationEndCommand invalidSecond = end(
                "orders", fixture.order().id(), 2, 1, AggregationKind.NONE);

        assertThrows(SemanticException.class,
                () -> relationshipService.createAssociation(fixture.model().id(),
                        new CreateUmlAssociationCommand(null, null, null, first, invalidSecond)));

        assertEquals(revisionBefore, modelService.getModel(fixture.model().id()).revision());
        assertEquals(elementsBefore, elementRepository.count());
        assertEquals(endsBefore, associationEndRepository.count());
    }

    @Test
    void persistsRoleNamesVisibilityAndNavigabilityOnAssociationEndUpdates() {
        Fixture fixture = fixture();
        UmlAssociationResponse created = relationshipService.createAssociation(
                fixture.model().id(), association(
                        fixture.customer().id(), fixture.order().id(), AggregationKind.NONE));
        long revision = created.modelRevision();

        for (Visibility visibility : Visibility.values()) {
            relationshipService.updateAssociation(fixture.model().id(), created.id(),
                    new UpdateUmlAssociationCommand(null, null, null,
                            new UmlAssociationEndCommand("customer", fixture.customer().id(),
                                    visibility, 1, 1, false, AggregationKind.NONE,
                                    AssociationEndOwnership.ASSOCIATION),
                            new UmlAssociationEndCommand("sales", fixture.order().id(),
                                    Visibility.PRIVATE, 0, null, true, AggregationKind.NONE,
                                    AssociationEndOwnership.ASSOCIATION)));
            UmlAssociationResponse reloaded = relationshipService.listAssociations(
                    fixture.model().id()).getFirst();
            assertEquals("customer", reloaded.ends().get(0).roleName());
            assertEquals(visibility, reloaded.ends().get(0).visibility());
            assertEquals(false, reloaded.ends().get(0).navigable());
            assertEquals("sales", reloaded.ends().get(1).roleName());
            assertEquals(Visibility.PRIVATE, reloaded.ends().get(1).visibility());
            assertEquals(true, reloaded.ends().get(1).navigable());
            assertNull(reloaded.ends().get(1).upperBound());
            assertEquals(++revision, modelService.getModel(fixture.model().id()).revision());
            assertEquals(visibility.name(), jdbcTemplate.queryForObject(
                    "SELECT visibility FROM uml_properties WHERE element_id = ?",
                    String.class, reloaded.ends().get(0).propertyId()));
        }
    }

    private Fixture fixture() {
        UmlModelResponse model = persistModel();
        return new Fixture(model,
                persistClassifier(model, "Customer", ClassifierKind.CLASS),
                persistClassifier(model, "Order", ClassifierKind.CLASS));
    }

    private UmlModelResponse persistModel() {
        Project project = projectRepository.saveAndFlush(
                new Project(uniqueName("Project"), "Relationships integration test"));
        return modelService.createModel(project.getId(), new CreateUmlModelCommand(uniqueName("Model"), null));
    }

    private UmlClassifierResponse persistClassifier(
            UmlModelResponse model, String name, ClassifierKind kind) {
        return modelService.createClassifier(model.id(), new CreateUmlClassifierCommand(
                uniqueName(name), null, null, kind, Visibility.PUBLIC, false, false));
    }

    private CreateUmlAssociationCommand association(
            UUID firstClassifierId, UUID secondClassifierId, AggregationKind aggregation) {
        return new CreateUmlAssociationCommand(null, null, null,
                end("customer", firstClassifierId, 1, 1, aggregation),
                end("orders", secondClassifierId, 0, null, AggregationKind.NONE));
    }

    private UmlAssociationEndCommand end(
            String roleName, UUID classifierId, int lower, Integer upper, AggregationKind aggregation) {
        return new UmlAssociationEndCommand(
                roleName, classifierId, Visibility.PUBLIC, lower, upper, true,
                aggregation, AssociationEndOwnership.ASSOCIATION);
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record Fixture(
            UmlModelResponse model, UmlClassifierResponse customer, UmlClassifierResponse order) {
    }
}
