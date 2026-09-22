package com.casegen.modeling.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlOperationCommand;
import com.casegen.modeling.command.CreateUmlParameterCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.dto.UmlOperationResponse;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.DiagramRepresentationCleanupService;
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
@Import({UmlModelService.class, DiagramRepresentationCleanupService.class,
        UmlFeaturesPersistenceIntegrationTest.AuthorizationTestConfig.class})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UmlFeaturesPersistenceIntegrationTest {

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

    private final UmlModelService service;
    private final ProjectRepository projectRepository;
    private final UmlElementRepository elementRepository;
    private final JdbcTemplate jdbcTemplate;

    UmlFeaturesPersistenceIntegrationTest(
            UmlModelService service,
            ProjectRepository projectRepository,
            UmlElementRepository elementRepository,
            JdbcTemplate jdbcTemplate) {
        this.service = service;
        this.projectRepository = projectRepository;
        this.elementRepository = elementRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationV5IsApplied() {
        Integer applied = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = true AND version = '5'
                """, Integer.class);

        assertEquals(1, applied);
    }

    @Test
    void createsCustomerPropertiesRegisterOperationAndParameters() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse customer = persistClass(model, "Customer");

        var id = service.createProperty(model.id(), customer.id(), primitiveProperty("id", PrimitiveType.STRING));
        service.createProperty(model.id(), customer.id(), primitiveProperty("name", PrimitiveType.STRING));
        service.createProperty(model.id(), customer.id(), primitiveProperty("email", PrimitiveType.STRING));
        UmlOperationResponse register = service.createOperation(
                model.id(), customer.id(), operation("register"));
        var input = service.createParameter(
                model.id(), register.id(), primitiveParameter(
                        "email", ParameterDirection.IN, PrimitiveType.STRING, 0));
        var result = service.createParameter(
                model.id(), register.id(), primitiveParameter(
                        null, ParameterDirection.RETURN, PrimitiveType.BOOLEAN, 1));

        assertEquals(PrimitiveType.STRING, id.primitiveType());
        assertNull(id.typeClassifierId());
        assertEquals(ParameterDirection.IN, input.direction());
        assertEquals(ParameterDirection.RETURN, result.direction());
        assertEquals(PrimitiveType.BOOLEAN, result.primitiveType());
        assertEquals(7, result.modelRevision());
        assertEquals(7, service.getModel(model.id()).revision());
        assertEquals(3, service.listProperties(model.id(), customer.id()).size());
        assertEquals(1, service.listOperations(model.id(), customer.id()).size());
        assertEquals(2, service.listParameters(model.id(), register.id()).size());
    }

    @Test
    void createsPropertyTypedByClassifierInSameModel() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse customer = persistClass(model, "Customer");
        UmlClassifierResponse address = persistClass(model, "Address");

        var property = service.createProperty(model.id(), customer.id(), new CreateUmlPropertyCommand(
                "address", null, null, TypeKind.CLASSIFIER, null, address.id(), 1, 1,
                false, false, false));

        assertEquals(TypeKind.CLASSIFIER, property.typeKind());
        assertEquals(address.id(), property.typeClassifierId());
        assertNull(property.primitiveType());
        assertEquals(3, property.modelRevision());
    }

    @Test
    void rejectsSecondReturnWithoutChangingElementsOrRevision() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse customer = persistClass(model, "Customer");
        UmlOperationResponse operation = service.createOperation(
                model.id(), customer.id(), operation("register"));
        service.createParameter(model.id(), operation.id(), primitiveParameter(
                null, ParameterDirection.RETURN, PrimitiveType.BOOLEAN, 0));
        long elementsBefore = elementRepository.count();
        long revisionBefore = service.getModel(model.id()).revision();

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createParameter(model.id(), operation.id(), primitiveParameter(
                        null, ParameterDirection.RETURN, PrimitiveType.STRING, 1)));

        assertEquals(SemanticErrorCode.RETURN_PARAMETER_ALREADY_EXISTS, exception.getCode());
        assertEquals(elementsBefore, elementRepository.count());
        assertEquals(revisionBefore, service.getModel(model.id()).revision());
    }

    @Test
    void rejectsCrossModelOwnerAndTypeBeforeCreatingRootElement() {
        Project project = persistProject();
        UmlModelResponse first = service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("First"), null));
        UmlModelResponse second = service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("Second"), null));
        UmlClassifierResponse firstClassifier = persistClass(first, "Customer");
        UmlClassifierResponse secondClassifier = persistClass(second, "Address");
        long elementsBefore = elementRepository.count();
        long secondRevisionBefore = service.getModel(second.id()).revision();

        SemanticException ownerMismatch = assertThrows(SemanticException.class,
                () -> service.createProperty(second.id(), firstClassifier.id(),
                        primitiveProperty("id", PrimitiveType.STRING)));
        SemanticException typeMismatch = assertThrows(SemanticException.class,
                () -> service.createProperty(second.id(), secondClassifier.id(),
                        new CreateUmlPropertyCommand(
                                "customer", null, null, TypeKind.CLASSIFIER, null,
                                firstClassifier.id(), 1, 1, false, false, false)));

        assertEquals(SemanticErrorCode.CLASSIFIER_MODEL_MISMATCH, ownerMismatch.getCode());
        assertEquals(SemanticErrorCode.TYPE_CLASSIFIER_MODEL_MISMATCH, typeMismatch.getCode());
        assertEquals(elementsBefore, elementRepository.count());
        assertEquals(secondRevisionBefore, service.getModel(second.id()).revision());
    }

    @Test
    void rejectsTypeXorAndInvalidMultiplicityAtomically() {
        UmlModelResponse model = persistModel();
        UmlClassifierResponse customer = persistClass(model, "Customer");
        long elementsBefore = elementRepository.count();
        long revisionBefore = service.getModel(model.id()).revision();

        SemanticException invalidType = assertThrows(SemanticException.class,
                () -> service.createProperty(model.id(), customer.id(), new CreateUmlPropertyCommand(
                        "invalidType", null, null, TypeKind.PRIMITIVE, PrimitiveType.STRING,
                        customer.id(), 1, 1, false, false, false)));
        SemanticException invalidMultiplicity = assertThrows(SemanticException.class,
                () -> service.createProperty(model.id(), customer.id(), new CreateUmlPropertyCommand(
                        "invalidMultiplicity", null, null, TypeKind.PRIMITIVE, PrimitiveType.STRING,
                        null, 2, 1, false, false, false)));

        assertEquals(SemanticErrorCode.INVALID_TYPE_REFERENCE, invalidType.getCode());
        assertEquals(SemanticErrorCode.INVALID_MULTIPLICITY, invalidMultiplicity.getCode());
        assertEquals(elementsBefore, elementRepository.count());
        assertEquals(revisionBefore, service.getModel(model.id()).revision());
    }

    private UmlModelResponse persistModel() {
        Project project = persistProject();
        return service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("Model"), null));
    }

    private Project persistProject() {
        return projectRepository.saveAndFlush(
                new Project(uniqueName("Project"), "UML features integration test"));
    }

    private UmlClassifierResponse persistClass(UmlModelResponse model, String name) {
        return service.createClassifier(model.id(), new CreateUmlClassifierCommand(
                name, null, null, ClassifierKind.CLASS, Visibility.PUBLIC, false, false));
    }

    private CreateUmlPropertyCommand primitiveProperty(String name, PrimitiveType type) {
        return new CreateUmlPropertyCommand(
                name, null, null, TypeKind.PRIMITIVE, type, null, 1, 1,
                false, false, false);
    }

    private CreateUmlOperationCommand operation(String name) {
        return new CreateUmlOperationCommand(name, null, null, false, false, false);
    }

    private CreateUmlParameterCommand primitiveParameter(
            String name, ParameterDirection direction, PrimitiveType type, int position) {
        return new CreateUmlParameterCommand(
                name, null, direction, TypeKind.PRIMITIVE, type, null, 1, 1, position);
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
