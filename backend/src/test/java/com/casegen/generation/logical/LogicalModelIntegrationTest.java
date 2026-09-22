package com.casegen.generation.logical;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.generation.dto.*;
import com.casegen.generation.service.GenerationProfileService;
import com.casegen.generation.logical.service.LogicalModelService;
import com.casegen.modeling.command.*;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.service.*;
import com.casegen.project.domain.*;
import com.casegen.project.repository.*;
import com.casegen.validation.service.ValidationService;
import com.casegen.versioning.service.ModelVersionService;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="DB_URL",matches="jdbc:postgresql:.*")
@TestConstructor(autowireMode=TestConstructor.AutowireMode.ALL)
class LogicalModelIntegrationTest {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UmlModelService models;
    private final UmlRelationshipService relationships;
    private final ValidationService validation;
    private final ModelVersionService versions;
    private final GenerationProfileService profiles;
    private final LogicalModelService logical;
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private final JsonMapper json=JsonMapper.builder().build();
    LogicalModelIntegrationTest(UserRepository users,ProjectRepository projects,ProjectMemberRepository members,UmlModelService models,
            UmlRelationshipService relationships,ValidationService validation,ModelVersionService versions,GenerationProfileService profiles,
            LogicalModelService logical,JdbcTemplate jdbc,MockMvc mvc) {
        this.users=users;this.projects=projects;this.members=members;this.models=models;this.relationships=relationships;
        this.validation=validation;this.versions=versions;this.profiles=profiles;this.logical=logical;this.jdbc=jdbc;this.mvc=mvc;
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void previewUsesSameTransformationWithoutPersistingLogicalRows() throws Exception {
        Fixture f=fixture();long revision=models.getModel(f.model).revision();
        var plan=logical.preview(f.version,f.profile);assertTrue(plan.valid());assertEquals(2,plan.tables().size());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM logical_models WHERE model_version_id=?",Integer.class,f.version));
        var built=logical.build(f.version,f.profile);assertEquals(plan.tables(),built.tables());assertEquals(revision,models.getModel(f.model).revision());
        mvc.perform(post("/api/model-versions/"+f.version+"/logical-models/preview").with(user(f.actor.getId().toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"generationProfileId\":\""+f.profile+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.tables.length()").value(2)).andExpect(jsonPath("$.issues.length()").value(0));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM logical_models WHERE model_version_id=?",Integer.class,f.version));
    }
    @Test void historyListsFrozenProfileNameAndHonorsProjectReadPermissions() throws Exception {
        Fixture f=fixture();var result=logical.build(f.version,f.profile);
        var profile=profiles.get(f.model,f.profile);profiles.save(f.model,f.profile,new GenerationProfileRequest("Renamed editable profile",GenerationProfileRequest.Target.SPRING_BOOT,profile.groupId(),profile.artifactId(),profile.basePackage(),profile.javaVersion(),profile.springBootVersion(),GenerationProfileRequest.Database.POSTGRESQL,profile.configJson()));
        User viewer=account(),outsider=account();members.saveAndFlush(new ProjectMember(f.project,viewer,ProjectMemberRole.VIEWER));
        mvc.perform(get("/api/uml-models/"+f.model+"/logical-models").with(user(viewer.getId().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(result.id().toString())).andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$[0].profileName").value("Backend")).andExpect(jsonPath("$[0].checksum").value(result.checksum())).andExpect(jsonPath("$[0].createdAt").exists());
        mvc.perform(get("/api/uml-models/"+f.model+"/logical-models").with(user(outsider.getId().toString()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/model-versions/"+f.version+"/logical-models/preview").with(user(viewer.getId().toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"generationProfileId\":\""+f.profile+"\"}")).andExpect(status().isForbidden());
        f.project.changeVisibility(ProjectVisibility.PUBLIC);projects.saveAndFlush(f.project);
        mvc.perform(get("/api/uml-models/"+f.model+"/logical-models").with(user(outsider.getId().toString()))).andExpect(status().isOk());
    }
    @Test void persistsRelationalRowsAndStableChecksumFromImmutableVersionAndProfile() throws Exception {
        Fixture f=fixture();long revision=models.getModel(f.model).revision();
        var first=logical.build(f.version,f.profile);var second=logical.build(f.version,f.profile);
        assertEquals("VALID",first.status());assertNotEquals(first.id(),second.id());assertEquals(first.checksum(),second.checksum());assertEquals(64,first.checksum().length());
        assertEquals(2,first.tables().size());assertEquals(first.tables(),logical.get(first.id()).tables());assertEquals(revision,models.getModel(f.model).revision());
        assertEquals(2,count("logical_tables",first.id()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM logical_primary_key_columns p JOIN logical_tables t ON t.id=p.table_id WHERE t.logical_model_id=?",Integer.class,first.id()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM logical_foreign_key_columns p JOIN logical_tables t ON t.id=p.table_id WHERE t.logical_model_id=?",Integer.class,first.id()));
        mvc.perform(get("/api/logical-models/"+first.id()).with(user(f.actor.getId().toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        mvc.perform(get("/api/logical-models/"+first.id()+"/tables").with(user(f.actor.getId().toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void editableChangesAndLaterProfileChangesCannotAlterExistingLogicalModel() {
        Fixture f=fixture();var result=logical.build(f.version,f.profile);String frozen=result.profileSnapshot().toString();
        String semantic=versions.list(f.model).getFirst().semanticSnapshot().toString();
        jdbc.update("UPDATE uml_elements SET name='ChangedEditableCustomer' WHERE id=?",f.customer);
        var rebuilt=logical.build(f.version,f.profile);assertEquals(result.checksum(),rebuilt.checksum());assertEquals(result.tables(),rebuilt.tables());
        assertEquals(semantic,versions.list(f.model).getFirst().semanticSnapshot().toString());
        var profile=profiles.get(f.model,f.profile);profiles.save(f.model,f.profile,new GenerationProfileRequest("Changed profile",GenerationProfileRequest.Target.SPRING_BOOT,profile.groupId(),profile.artifactId(),profile.basePackage(),21,"4.1.1",GenerationProfileRequest.Database.POSTGRESQL,json.valueToTree(Map.of("inheritanceStrategy","OTHER"))));
        assertEquals(frozen,logical.get(result.id()).profileSnapshot().toString());assertEquals(result.checksum(),logical.get(result.id()).checksum());
        var invalid=logical.build(f.version,f.profile);assertEquals("INVALID",invalid.status());assertTrue(invalid.tables().isEmpty());assertNotEquals(result.checksum(),invalid.checksum());
    }
    @Test void invalidConfigurationPersistsIssuesNotPartialTablesAndPostUsesDto() throws Exception {
        Fixture f=fixture();var mapping=profiles.mappings(f.model,f.profile).stream().filter(m->m.umlElementId().equals(f.customer)).findFirst().orElseThrow();
        profiles.saveMapping(f.model,f.profile,mapping.id(),new ElementMappingRequest(f.customer,ElementMappingRequest.TargetKind.ENTITY,null));
        var invalid=logical.build(f.version,f.profile);assertEquals("INVALID",invalid.status());assertEquals(0,count("logical_tables",invalid.id()));
        assertTrue(invalid.issues().stream().anyMatch(i->i.code().equals("ENTITY_ID_REQUIRED")));assertEquals(invalid.issues(),logical.get(invalid.id()).issues());
        mvc.perform(post("/api/model-versions/"+f.version+"/logical-models").with(user(f.actor.getId().toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"generationProfileId\":\""+f.profile+"\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVALID"));
        mvc.perform(post("/api/model-versions/"+f.version+"/logical-models").with(user(f.actor.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
    @Test void viewerPublicVisitorAndOutsiderCannotBuildButCollaboratorCan() throws Exception {
        Fixture f=fixture();var result=logical.build(f.version,f.profile);User viewer=account(),collaborator=account(),outsider=account();
        members.saveAndFlush(new ProjectMember(f.project,viewer,ProjectMemberRole.VIEWER));members.saveAndFlush(new ProjectMember(f.project,collaborator,ProjectMemberRole.COLLABORATOR));
        buildRequest(f,viewer,403);buildRequest(f,outsider,403);
        mvc.perform(get("/api/logical-models/"+result.id()).with(user(viewer.getId().toString()))).andExpect(status().isOk());
        mvc.perform(get("/api/logical-models/"+result.id()).with(user(outsider.getId().toString()))).andExpect(status().isForbidden());
        f.project.changeVisibility(ProjectVisibility.PUBLIC);projects.saveAndFlush(f.project);
        mvc.perform(get("/api/logical-models/"+result.id()).with(user(outsider.getId().toString()))).andExpect(status().isOk());buildRequest(f,outsider,403);buildRequest(f,collaborator,201);
    }
    @Test void crossModelProfileIsRejected() throws Exception {
        Fixture a=fixture(),b=fixture();
        mvc.perform(post("/api/model-versions/"+a.version+"/logical-models").with(user(a.actor.getId().toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"generationProfileId\":\""+b.profile+"\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/model-versions/"+a.version+"/logical-models/preview").with(user(a.actor.getId().toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"generationProfileId\":\""+b.profile+"\"}")).andExpect(status().isNotFound());
    }
    @Test void joinedInheritedIdentifierPassesValidationAndPersistsPkFk() {
        Fixture f=fixture();UUID child=classifier(f.model,"PreferredCustomer");
        UUID inheritedId=UUID.fromString(profiles.mappings(f.model,f.profile).stream().filter(m->m.umlElementId().equals(f.customer)).findFirst().orElseThrow().configJson().path("idPropertyId").asText());
        relationships.createGeneralization(f.model,new CreateUmlGeneralizationCommand(child,f.customer,true,null));
        profiles.saveMapping(f.model,f.profile,null,new ElementMappingRequest(child,ElementMappingRequest.TargetKind.ENTITY,json.valueToTree(Map.of("idPropertyId",inheritedId.toString()))));
        assertEquals("VALID",validation.validate(f.model).status());UUID version=versions.create(f.model).id();
        var result=logical.build(version,f.profile);assertEquals("VALID",result.status());
        var table=result.tables().stream().filter(t->t.name().equals("preferred_customer")).findFirst().orElseThrow();
        assertEquals(table.primaryKey().columns().getFirst(),table.foreignKeys().getFirst().columns().getFirst().column());assertEquals("customer",table.foreignKeys().getFirst().referencedTable());
    }
    private void buildRequest(Fixture f,User actor,int status) throws Exception { mvc.perform(post("/api/model-versions/"+f.version+"/logical-models").with(user(actor.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{\"generationProfileId\":\""+f.profile+"\"}")).andExpect(status().is(status)); }
    private int count(String table,UUID id) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE logical_model_id=?",Integer.class,id); }
    private User account() { return users.saveAndFlush(new User(UUID.randomUUID()+"@test.local","hashed","V9 tester")); }
    private Fixture fixture() {
        User actor=account();Project project=projects.saveAndFlush(new Project("V9 "+UUID.randomUUID(),null));members.saveAndFlush(new ProjectMember(project,actor,ProjectMemberRole.OWNER));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getId().toString(),"",List.of()));
        UUID model=models.createModel(project.getId(),new CreateUmlModelCommand("V9 Model",null)).id();
        UUID customer=classifier(model,"Customer"),sale=classifier(model,"Sale");UUID customerId=property(model,customer,"customerKey",1),saleId=property(model,sale,"id",1);property(model,customer,"email",0);
        relationships.createAssociation(model,new CreateUmlAssociationCommand(null,null,null,end(customer,"customer",1,1),end(sale,"sales",0,null)));
        var profile=profiles.save(model,null,new GenerationProfileRequest("Backend",GenerationProfileRequest.Target.SPRING_BOOT,"com.test","backend","com.test.backend",21,"4.1.1",GenerationProfileRequest.Database.POSTGRESQL,null));
        profiles.saveMapping(model,profile.id(),null,new ElementMappingRequest(customer,ElementMappingRequest.TargetKind.ENTITY,json.valueToTree(Map.of("idPropertyId",customerId.toString()))));
        profiles.saveMapping(model,profile.id(),null,new ElementMappingRequest(sale,ElementMappingRequest.TargetKind.ENTITY,json.valueToTree(Map.of("idPropertyId",saleId.toString()))));
        assertEquals("VALID",validation.validate(model).status());UUID version=versions.create(model).id();return new Fixture(actor,project,model,customer,profile.id(),version);
    }
    private UUID classifier(UUID model,String name) { return models.createClassifier(model,new CreateUmlClassifierCommand(name,null,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false)).id(); }
    private UUID property(UUID model,UUID owner,String name,int lower) { return models.createProperty(model,owner,new CreateUmlPropertyCommand(name,null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.STRING,null,lower,1,false,false,false)).id(); }
    private UmlAssociationEndCommand end(UUID classifier,String name,int lower,Integer upper) { return new UmlAssociationEndCommand(name,classifier,Visibility.PUBLIC,lower,upper,false,AggregationKind.NONE,AssociationEndOwnership.ASSOCIATION); }
    private record Fixture(User actor,Project project,UUID model,UUID customer,UUID profile,UUID version) {}
}
