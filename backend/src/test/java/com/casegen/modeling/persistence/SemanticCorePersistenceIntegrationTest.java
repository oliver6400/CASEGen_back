package com.casegen.modeling.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlPackageCommand;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UmlModelService.class, SemanticCorePersistenceIntegrationTest.AuthorizationTestConfig.class})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SemanticCorePersistenceIntegrationTest {

    @TestConfiguration
    static class AuthorizationTestConfig {

        @Bean
        ProjectAuthorizationService projectAuthorizationService() {
            return org.mockito.Mockito.mock(ProjectAuthorizationService.class);
        }
    }

    private final UmlModelService service;
    private final ProjectRepository projectRepository;
    private final UmlElementRepository elementRepository;
    private final JdbcTemplate jdbcTemplate;

    SemanticCorePersistenceIntegrationTest(
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
    void appliesMigrationsV1ThroughV3() {
        Integer applied = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = true AND version IN ('1', '2', '3')
                """, Integer.class);

        assertEquals(3, applied);
    }

    @Test
    void createsModelWithRevisionZeroAndRejectsDuplicateName() {
        Project project = persistProject();
        String name = uniqueName("Domain");

        var model = service.createModel(project.getId(), new CreateUmlModelCommand(name, "Main model"));

        assertNotNull(model.id());
        assertEquals(0, model.revision());
        assertEquals(0, model.lockVersion());

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createModel(project.getId(), new CreateUmlModelCommand(name, null)));
        assertEquals(SemanticErrorCode.DUPLICATE_MODEL_NAME, exception.getCode());
    }

    @Test
    void createsRootAndChildPackagesAndIncrementsRevisionOncePerMutation() {
        var model = persistModel();

        var root = service.createPackage(model.id(),
                new CreateUmlPackageCommand("domain", null, null));
        var child = service.createPackage(model.id(),
                new CreateUmlPackageCommand("sales", null, root.id()));

        assertEquals(UmlElementType.PACKAGE, root.elementType());
        assertEquals(root.id(), child.parentPackageId());
        assertEquals(1, root.modelRevision());
        assertEquals(2, child.modelRevision());
        assertEquals(2, service.getModel(model.id()).revision());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM uml_packages WHERE element_id IN (?, ?)",
                Integer.class, root.id(), child.id()));
    }

    @Test
    void createsClassAndInterfaceWithoutOrphanElements() {
        var model = persistModel();
        var umlPackage = service.createPackage(model.id(),
                new CreateUmlPackageCommand("domain", null, null));

        var modelClass = service.createClassifier(model.id(), new CreateUmlClassifierCommand(
                "Customer", null, umlPackage.id(), ClassifierKind.CLASS,
                Visibility.PUBLIC, false, true));
        var modelInterface = service.createClassifier(model.id(), new CreateUmlClassifierCommand(
                "Identifiable", null, umlPackage.id(), ClassifierKind.INTERFACE,
                Visibility.PUBLIC, true, false));

        assertEquals(UmlElementType.CLASSIFIER, modelClass.elementType());
        assertEquals(ClassifierKind.CLASS, modelClass.kind());
        assertEquals(ClassifierKind.INTERFACE, modelInterface.kind());
        assertEquals(3, modelInterface.modelRevision());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM uml_classifiers WHERE element_id IN (?, ?)",
                Integer.class, modelClass.id(), modelInterface.id()));
    }

    @Test
    void rejectsActiveInterfaceWithoutLeavingRootElement() {
        var model = persistModel();
        long before = elementRepository.count();

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createClassifier(model.id(), new CreateUmlClassifierCommand(
                        "ActiveInterface", null, null, ClassifierKind.INTERFACE,
                        Visibility.PUBLIC, false, true)));

        assertEquals(SemanticErrorCode.INTERFACE_CANNOT_BE_ACTIVE, exception.getCode());
        assertEquals(before, elementRepository.count());
        assertEquals(0, service.getModel(model.id()).revision());
    }

    @Test
    void rejectsParentPackageFromAnotherModel() {
        Project project = persistProject();
        var first = service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("First"), null));
        var second = service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("Second"), null));
        var packageFromFirst = service.createPackage(first.id(),
                new CreateUmlPackageCommand("domain", null, null));

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createPackage(second.id(),
                        new CreateUmlPackageCommand("child", null, packageFromFirst.id())));

        assertEquals(SemanticErrorCode.PACKAGE_MODEL_MISMATCH, exception.getCode());
        assertEquals(0, service.getModel(second.id()).revision());
    }

    @Test
    void rejectsClassifierPackageFromAnotherModel() {
        Project project = persistProject();
        var first = service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("First"), null));
        var second = service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("Second"), null));
        var packageFromFirst = service.createPackage(first.id(),
                new CreateUmlPackageCommand("domain", null, null));

        SemanticException exception = assertThrows(SemanticException.class,
                () -> service.createClassifier(second.id(), new CreateUmlClassifierCommand(
                        "Customer", null, packageFromFirst.id(), ClassifierKind.CLASS,
                        Visibility.PUBLIC, false, false)));

        assertEquals(SemanticErrorCode.CLASSIFIER_PACKAGE_MODEL_MISMATCH, exception.getCode());
        assertEquals(0, service.getModel(second.id()).revision());
    }

    @Test
    void updatesJpaLockVersionWithSemanticMutation() {
        var model = persistModel();

        service.createPackage(model.id(), new CreateUmlPackageCommand("domain", null, null));

        Long lockVersion = jdbcTemplate.queryForObject(
                "SELECT lock_version FROM uml_models WHERE id = ?", Long.class, model.id());
        assertEquals(1, lockVersion);
    }

    private com.casegen.modeling.dto.UmlModelResponse persistModel() {
        Project project = persistProject();
        return service.createModel(project.getId(),
                new CreateUmlModelCommand(uniqueName("Model"), null));
    }

    private Project persistProject() {
        return projectRepository.saveAndFlush(
                new Project(uniqueName("Project"), "Semantic core integration test"));
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
