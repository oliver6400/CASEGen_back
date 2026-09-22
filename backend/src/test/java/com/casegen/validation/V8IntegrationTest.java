package com.casegen.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.generation.dto.*;
import com.casegen.generation.service.GenerationProfileService;
import com.casegen.modeling.command.*;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.dto.*;
import com.casegen.shared.exception.WorkflowConflictException;
import com.casegen.modeling.service.*;
import com.casegen.project.domain.*;
import com.casegen.project.repository.*;
import com.casegen.validation.service.ValidationService;
import com.casegen.versioning.service.ModelVersionService;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="DB_URL", matches="jdbc:postgresql:.*")
@TestConstructor(autowireMode=TestConstructor.AutowireMode.ALL)
class V8IntegrationTest {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UmlModelService models;
    private final ValidationService validation;
    private final ModelVersionService versions;
    private final GenerationProfileService profiles;
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private final EntityManager em;
    private final DiagramService diagrams;
    V8IntegrationTest(UserRepository users, ProjectRepository projects, ProjectMemberRepository members, UmlModelService models,
            ValidationService validation, ModelVersionService versions, GenerationProfileService profiles, JdbcTemplate jdbc, MockMvc mvc, EntityManager em, DiagramService diagrams) {
        this.users=users; this.projects=projects; this.members=members; this.models=models; this.validation=validation;
        this.versions=versions; this.profiles=profiles; this.jdbc=jdbc; this.mvc=mvc; this.em=em;
        this.diagrams=diagrams;
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void validModelCreatesIncrementalStableVersionsWithDetachedSnapshots() {
        var f = fixture(ProjectMemberRole.OWNER);
        assertThrows(ResponseStatusException.class, () -> versions.create(f.model.id()));
        var run = validation.validate(f.model.id());
        assertEquals("VALID", run.status());
        assertTrue(run.current());
        var first = versions.create(f.model.id());
        var duplicate = assertThrows(WorkflowConflictException.class,()->versions.create(f.model.id()));
        assertEquals("MODEL_REVISION_ALREADY_VERSIONED",duplicate.getCode());
        assertEquals(1,first.versionNumber());
        assertEquals(run.id(), first.validationRunId());
        assertEquals(first.checksum(),versions.list(f.model.id()).getFirst().checksum());
        assertEquals(64, first.checksum().length());
        assertTrue(first.semanticSnapshot().has("properties"));
        assertTrue(first.semanticSnapshot().has("associationEnds"));
        assertTrue(first.diagramSnapshot().has("nodes"));
        String frozen = first.semanticSnapshot().toString();
        classifier(f.model.id(),"Later");
        assertFalse(validation.get(run.id()).current());
        assertThrows(ResponseStatusException.class, () -> versions.create(f.model.id()));
        assertEquals(frozen,versions.list(f.model.id()).getFirst().semanticSnapshot().toString());
    }
    @Test void eachSemanticRevisionCanBeRevalidatedAndVersionedWithoutHistoricalBlocking() throws Exception {
        var f=fixture(ProjectMemberRole.OWNER);classifier(f.model.id(),"Customer");
        String run1=validateRevision(f,1);
        mvc.perform(post("/api/uml-models/"+f.model.id()+"/versions").with(user(f.actor.getId().toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(1)).andExpect(jsonPath("$.semanticSnapshot.model.revision").value(1));
        authenticate(f.actor);String frozen=versions.list(f.model.id()).getFirst().semanticSnapshot().toString();classifier(f.model.id(),"Sale");
        mvc.perform(get("/api/validations/"+run1).with(user(f.actor.getId().toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.current").value(false));
        mvc.perform(post("/api/uml-models/"+f.model.id()+"/versions").with(user(f.actor.getId().toString())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VALIDATION_STALE"));
        String run2=validateRevision(f,2);assertNotEquals(run1,run2);
        mvc.perform(post("/api/uml-models/"+f.model.id()+"/versions").with(user(f.actor.getId().toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(2)).andExpect(jsonPath("$.semanticSnapshot.model.revision").value(2));
        mvc.perform(post("/api/uml-models/"+f.model.id()+"/versions").with(user(f.actor.getId().toString())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODEL_REVISION_ALREADY_VERSIONED"))
                .andExpect(jsonPath("$.message").value("La revisión r2 ya está versionada como v2."));
        // Revalidating even an already versioned revision creates a new run, not a 409.
        assertNotEquals(run2,validateRevision(f,2));
        authenticate(f.actor);models.createClassifier(f.model.id(),new CreateUmlClassifierCommand("Identifiable",null,null,ClassifierKind.INTERFACE,Visibility.PUBLIC,false,false));
        validateRevision(f,3);
        mvc.perform(post("/api/uml-models/"+f.model.id()+"/versions").with(user(f.actor.getId().toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(3));
        authenticate(f.actor);var all=versions.list(f.model.id());assertEquals(3,all.size());assertEquals(frozen,all.getFirst().semanticSnapshot().toString());
        assertEquals(1,all.getFirst().semanticSnapshot().path("classifiers").size());assertEquals(2,all.get(1).semanticSnapshot().path("classifiers").size());assertEquals(3,all.get(2).semanticSnapshot().path("classifiers").size());
    }
    @Test void geometryChangesDoNotInvalidateSemanticValidationOrAllowDuplicateVersion() {
        var f=fixture(ProjectMemberRole.OWNER);UUID classifier=classifier(f.model.id(),"Customer");
        var diagram=diagrams.createDiagram(f.model.id(),new CreateDiagramRequest("Main",DiagramType.CLASS,null));
        var node=diagrams.addNode(diagram.id(),new AddDiagramNodeRequest(classifier,0,0,200,100,0));
        var run=validation.validate(f.model.id());var version=versions.create(f.model.id());
        diagrams.updateNodeGeometry(diagram.id(),node.id(),new UpdateDiagramNodeGeometryRequest(123,456,300,160,1));
        assertTrue(validation.get(run.id()).current());assertEquals(1,models.getModel(f.model.id()).revision());
        assertThrows(WorkflowConflictException.class,()->versions.create(f.model.id()));
        assertEquals(version.semanticSnapshot(),versions.list(f.model.id()).getFirst().semanticSnapshot());
        assertEquals(123,diagrams.getDiagram(diagram.id()).nodes().getFirst().x());
    }
    @Test void futureDatedHistoricalValidationDoesNotReplaceNewRevisionOnReloadOrVersionCreation() throws Exception {
        var f=fixture(ProjectMemberRole.OWNER);classifier(f.model.id(),"Customer");
        var oldRun=validation.validate(f.model.id());var first=versions.create(f.model.id());
        jdbc.update("UPDATE validation_runs SET started_at=clock_timestamp()+INTERVAL '1 day' WHERE id=?",oldRun.id());
        classifier(f.model.id(),"Sale");var newRun=validation.validate(f.model.id());
        assertTrue(validation.get(oldRun.id()).startedAt().isAfter(newRun.startedAt()));
        assertEquals(newRun.id(),validation.latest(f.model.id()).orElseThrow().id());
        mvc.perform(get("/api/uml-models/"+f.model.id()+"/validations/latest").with(user(f.actor.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(newRun.id().toString()))
                .andExpect(jsonPath("$.modelRevision").value(2)).andExpect(jsonPath("$.current").value(true));
        mvc.perform(post("/api/uml-models/"+f.model.id()+"/versions").with(user(f.actor.getId().toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.validationRunId").value(newRun.id().toString()));
        authenticate(f.actor);
        assertEquals(first.semanticSnapshot(),versions.list(f.model.id()).getFirst().semanticSnapshot());
        assertEquals(newRun.id(),validation.latest(f.model.id()).orElseThrow().id());
    }
    @Test void latestRunWithinSameRevisionUsesCreationOrderEvenWhenOldValidRunHasFutureDate() {
        var f=fixture(ProjectMemberRole.OWNER);UUID customer=classifier(f.model.id(),"Customer");
        var oldRun=validation.validate(f.model.id());
        jdbc.update("UPDATE validation_runs SET started_at=clock_timestamp()+INTERVAL '1 day' WHERE id=?",oldRun.id());
        var repeatedRun=validation.validate(f.model.id());
        assertEquals(repeatedRun.id(),validation.latest(f.model.id()).orElseThrow().id());
        var profile=profiles.save(f.model.id(),null,profile("Backend"));
        profiles.saveMapping(f.model.id(),profile.id(),null,new ElementMappingRequest(customer,ElementMappingRequest.TargetKind.ENTITY,null));
        var invalidRun=validation.validate(f.model.id());
        assertEquals(oldRun.modelRevision(),invalidRun.modelRevision());
        assertEquals("INVALID",invalidRun.status());
        assertEquals(invalidRun.id(),validation.latest(f.model.id()).orElseThrow().id());
        assertTrue(validation.latest(f.model.id()).orElseThrow().current());
        assertEquals("VALIDATION_INVALID",assertThrows(WorkflowConflictException.class,()->versions.create(f.model.id())).getCode());
    }
    private String validateRevision(Fixture f,int revision) throws Exception {
        String body=mvc.perform(post("/api/uml-models/"+f.model.id()+"/validations").with(user(f.actor.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelRevision").value(revision)).andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.current").value(true)).andReturn().getResponse().getContentAsString();
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).path("id").asText();
    }
    @Test void invalidSemanticNameIsReportedAndCannotBeVersioned() {
        var f = fixture(ProjectMemberRole.OWNER);
        UUID id = classifier(f.model.id(),"Customer");
        jdbc.update("UPDATE uml_elements SET name='' WHERE id=?",id);
        var run = validation.validate(f.model.id());
        assertEquals("INVALID",run.status());
        assertTrue(run.issues().stream().anyMatch(i -> i.code().equals("INVALID_NAME") && id.equals(i.umlElementId())));
        assertThrows(ResponseStatusException.class,() -> versions.create(f.model.id()));
    }
    @Test void generationChangesInvalidateValidationAndEntityRequiresIdentifier() {
        var f = fixture(ProjectMemberRole.OWNER);
        UUID customer = classifier(f.model.id(),"Customer");
        validation.validate(f.model.id());
        var profile = profiles.save(f.model.id(),null,profile("Backend"));
        assertFalse(validation.latest(f.model.id()).orElseThrow().current());
        assertThrows(ResponseStatusException.class,() -> versions.create(f.model.id()));
        var mapping = profiles.saveMapping(f.model.id(),profile.id(),null,
                new ElementMappingRequest(customer,ElementMappingRequest.TargetKind.ENTITY,null));
        var invalid = validation.validate(f.model.id());
        assertEquals("INVALID",invalid.status());
        assertTrue(invalid.issues().stream().anyMatch(i -> i.code().equals("ENTITY_IDENTIFIER_REQUIRED")));
        models.createProperty(f.model.id(),customer,new CreateUmlPropertyCommand("id",null,Visibility.PRIVATE,
                TypeKind.PRIMITIVE,PrimitiveType.STRING,null,1,1,false,false,false));
        assertEquals("VALID",validation.validate(f.model.id()).status());
        var version = versions.create(f.model.id());
        assertEquals(1,version.semanticSnapshot().path("generation").path("mappings").size());
        profiles.saveMapping(f.model.id(),profile.id(),mapping.id(),new ElementMappingRequest(customer,ElementMappingRequest.TargetKind.IGNORE,null));
        assertFalse(validation.latest(f.model.id()).orElseThrow().current());
        assertEquals("ENTITY",versions.list(f.model.id()).getFirst().semanticSnapshot().path("generation").path("mappings").get(0).path("target_kind").asText());
    }
    @Test void mappingCannotReferenceAnotherModelAndProfileCrudIsScoped() {
        var f = fixture(ProjectMemberRole.OWNER);
        var profile = profiles.save(f.model.id(),null,profile("First"));
        var other = fixture(ProjectMemberRole.OWNER);
        UUID foreign = classifier(other.model.id(),"Other");
        authenticate(f.actor);
        assertThrows(ResponseStatusException.class,()->profiles.saveMapping(f.model.id(),profile.id(),null,new ElementMappingRequest(foreign,ElementMappingRequest.TargetKind.ENTITY,null)));
        profiles.save(f.model.id(),profile.id(),profile("Renamed"));
        assertEquals("Renamed",profiles.get(f.model.id(),profile.id()).name());
        profiles.delete(f.model.id(),profile.id()); assertTrue(profiles.list(f.model.id()).isEmpty());
    }
    @Test void modelVersionIsProtectedAgainstDatabaseUpdates() {
        var f = fixture(ProjectMemberRole.OWNER); validation.validate(f.model.id());
        var version = versions.create(f.model.id());
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE model_versions SET checksum='changed' WHERE id=?",version.id()));
    }
    @Test void ownerAndCollaboratorCanValidateAndVersionWhileViewerCannot() throws Exception {
        for (var role : new ProjectMemberRole[]{ProjectMemberRole.OWNER,ProjectMemberRole.COLLABORATOR}) {
            var f = fixture(role);
            mvc.perform(post("/api/uml-models/"+f.model.id()+"/validations").with(user(f.actor.getId().toString())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
            mvc.perform(post("/api/uml-models/"+f.model.id()+"/versions").with(user(f.actor.getId().toString())))
                    .andExpect(status().isCreated());
        }
        var f = fixture(ProjectMemberRole.OWNER);
        var run = validation.validate(f.model.id());
        User viewer = users.saveAndFlush(new User(UUID.randomUUID()+"@test.local","hashed","Viewer"));
        members.saveAndFlush(new ProjectMember(f.project,viewer,ProjectMemberRole.VIEWER));
        for (String operation : new String[]{"validations","versions"}) mvc.perform(post("/api/uml-models/"+f.model.id()+"/"+operation).with(user(viewer.getId().toString()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/validations/"+run.id()).with(user(viewer.getId().toString()))).andExpect(status().isOk());
        mvc.perform(post("/api/uml-models/"+f.model.id()+"/generation-profiles").with(user(viewer.getId().toString()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Backend","target":"SPRING_BOOT","groupId":"com.test","artifactId":"demo","basePackage":"com.test.demo","javaVersion":21,"springBootVersion":"4.1.1","database":"POSTGRESQL"}
                """)).andExpect(status().isForbidden());
    }
    private Fixture fixture(ProjectMemberRole role) {
        User actor = users.saveAndFlush(new User(UUID.randomUUID()+"@test.local","hashed","V8 tester"));
        Project project = projects.saveAndFlush(new Project("V8 "+UUID.randomUUID(),null));
        members.saveAndFlush(new ProjectMember(project,actor,role)); authenticate(actor);
        var model=models.createModel(project.getId(),new CreateUmlModelCommand("V8 Model",null)); em.flush();
        return new Fixture(actor,project,model);
    }
    private UUID classifier(UUID modelId,String name) { return models.createClassifier(modelId,new CreateUmlClassifierCommand(name,null,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false)).id(); }
    private void authenticate(User actor) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getId().toString(),"",java.util.List.of())); }
    private GenerationProfileRequest profile(String name) { return new GenerationProfileRequest(name,GenerationProfileRequest.Target.SPRING_BOOT,"com.test","demo","com.test.demo",21,"4.1.1",GenerationProfileRequest.Database.POSTGRESQL,null); }
    private record Fixture(User actor,Project project,UmlModelResponse model) {}
}
