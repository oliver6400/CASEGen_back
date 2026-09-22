package com.casegen.project;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.casegen.auth.domain.*;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.command.*;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.dto.CreateDiagramRequest;
import com.casegen.modeling.service.*;
import com.casegen.project.domain.*;
import com.casegen.project.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
class ProjectSharingIntegrationTest {
    private final MockMvc mvc;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectInvitationRepository invitations;
    private final UmlModelService models;
    private final DiagramService diagrams;
    private final JsonMapper json=JsonMapper.builder().build();
    ProjectSharingIntegrationTest(MockMvc mvc,UserRepository users,ProjectRepository projects,ProjectMemberRepository members,
            ProjectInvitationRepository invitations,UmlModelService models,DiagramService diagrams) {
        this.mvc=mvc;this.users=users;this.projects=projects;this.members=members;this.invitations=invitations;this.models=models;this.diagrams=diagrams;
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void privateProjectBlocksOutsidersAcrossProjectModelAndDiagram() throws Exception {
        var f=fixture();var outsider=account();
        assertEquals(ProjectVisibility.PRIVATE,f.project.getVisibility());
        for (String path:new String[]{"/api/v1/projects/"+f.project.getId(),"/api/projects/"+f.project.getId()+"/uml-models","/api/uml-models/"+f.model,"/api/uml-models/"+f.model+"/export/xmi","/api/diagrams/"+f.diagram})
            mvc.perform(get(path).with(user(outsider.getId().toString()))).andExpect(status().isForbidden());
        String list=mvc.perform(get("/api/v1/projects").with(user(outsider.getId().toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(list.contains(f.project.getId().toString()));
    }
    @Test void publicAuthenticatedVisitorCanReadButNeverEditOrManage() throws Exception {
        var f=fixture();var visitor=account();visibility(f,f.owner,"PUBLIC",200);
        for (String path:new String[]{"/api/v1/projects/"+f.project.getId(),"/api/projects/"+f.project.getId()+"/uml-models","/api/uml-models/"+f.model,"/api/uml-models/"+f.model+"/export/xmi","/api/uml-models/"+f.model+"/diagrams","/api/diagrams/"+f.diagram})
            mvc.perform(get(path).with(user(visitor.getId().toString()))).andExpect(status().isOk());
        mvc.perform(get("/api/projects/"+f.project.getId()+"/access").with(user(visitor.getId().toString()))).andExpect(jsonPath("$.projectRole").isEmpty()).andExpect(jsonPath("$.canView").value(true)).andExpect(jsonPath("$.canEdit").value(false)).andExpect(jsonPath("$.canGenerate").value(false)).andExpect(jsonPath("$.canManageMembers").value(false));
        for(String operation:new String[]{"validations","versions"}) mvc.perform(post("/api/uml-models/"+f.model+"/"+operation).with(user(visitor.getId().toString()))).andExpect(status().isForbidden());
        edit(f,visitor,403);visibility(f,visitor,"PRIVATE",403);
        mvc.perform(get("/api/projects/"+f.project.getId()+"/invitations").with(user(visitor.getId().toString()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/diagrams/"+f.diagram)).andExpect(status().isUnauthorized());
        String list=mvc.perform(get("/api/v1/projects").with(user(visitor.getId().toString()))).andReturn().getResponse().getContentAsString();
        assertTrue(list.contains(f.project.getId().toString()));
    }
    @Test void ownerInvitesCollaboratorWhoAcceptsAndCanEdit() throws Exception {
        var f=fixture();var recipient=account();UUID invite=invite(f,recipient,"COLLABORATOR");var other=account();
        mvc.perform(post("/api/project-invitations/"+invite+"/accept").with(user(other.getId().toString()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/project-invitations").with(user(recipient.getId().toString()))).andExpect(jsonPath("$[0].id").value(invite.toString()));
        accept(invite,recipient,200);edit(f,recipient,201);
        assertEquals(ProjectMemberRole.COLLABORATOR,members.findByProjectIdAndUserId(f.project.getId(),recipient.getId()).orElseThrow().getRole());
        accept(invite,recipient,409);
    }
    @Test void invitedViewerReadsButCannotEditValidateOrVersion() throws Exception {
        var f=fixture();var recipient=account();accept(invite(f,recipient,"VIEWER"),recipient,200);edit(f,recipient,403);
        mvc.perform(get("/api/diagrams/"+f.diagram).with(user(recipient.getId().toString()))).andExpect(status().isOk());
        mvc.perform(get("/api/uml-models/"+f.model+"/export/xmi").with(user(recipient.getId().toString())))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/xml"));
        for (String operation:new String[]{"validations","versions"}) mvc.perform(post("/api/uml-models/"+f.model+"/"+operation).with(user(recipient.getId().toString()))).andExpect(status().isForbidden());
    }
    @Test void ownerChangesRevokesAndReactivatesNonOwnerMembership() throws Exception {
        var f=fixture();var recipient=account();accept(invite(f,recipient,"COLLABORATOR"),recipient,200);
        var member=members.findByProjectIdAndUserId(f.project.getId(),recipient.getId()).orElseThrow();
        mvc.perform(patch("/api/projects/"+f.project.getId()+"/members/"+member.getId()+"/role").with(user(f.owner.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isOk());
        edit(f,recipient,403);
        mvc.perform(delete("/api/projects/"+f.project.getId()+"/members/"+member.getId()).with(user(f.owner.getId().toString()))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/"+f.project.getId()).with(user(recipient.getId().toString()))).andExpect(status().isForbidden());
        accept(invite(f,recipient,"COLLABORATOR"),recipient,200);edit(f,recipient,201);
        assertEquals(member.getId(),members.findByProjectIdAndUserId(f.project.getId(),recipient.getId()).orElseThrow().getId());
    }
    @Test void ownerCannotAccidentallyRemoveOrDowngradeOwner() throws Exception {
        var f=fixture();var owner=members.findByProjectIdAndUserId(f.project.getId(),f.owner.getId()).orElseThrow();
        mvc.perform(delete("/api/projects/"+f.project.getId()+"/members/"+owner.getId()).with(user(f.owner.getId().toString()))).andExpect(status().isConflict());
        mvc.perform(patch("/api/projects/"+f.project.getId()+"/members/"+owner.getId()+"/role").with(user(f.owner.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isConflict());
        assertEquals(ProjectMemberStatus.ACTIVE,owner.getStatus());assertEquals(ProjectMemberRole.OWNER,owner.getRole());
        mvc.perform(post("/api/projects/"+f.project.getId()+"/invitations").with(user(f.owner.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"other@test.local\",\"role\":\"OWNER\"}")).andExpect(status().isBadRequest());
    }
    @Test void onlyOwnerOrSuperuserCanManageMembersAndVisibility() throws Exception {
        var f=fixture();var recipient=account();accept(invite(f,recipient,"COLLABORATOR"),recipient,200);
        var member=members.findByProjectIdAndUserId(f.project.getId(),recipient.getId()).orElseThrow();
        visibility(f,recipient,"PUBLIC",403);
        mvc.perform(post("/api/projects/"+f.project.getId()+"/invitations").with(user(recipient.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"other@test.local\",\"role\":\"VIEWER\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/"+f.project.getId()+"/members/"+member.getId()).with(user(recipient.getId().toString()))).andExpect(status().isForbidden());
        User superuser=users.findFirstBySystemRole(SystemRole.SUPERUSER).orElseGet(()->users.saveAndFlush(new User(UUID.randomUUID()+"@test.local","hash","Superuser",SystemRole.SUPERUSER)));
        visibility(f,superuser,"PUBLIC",200);edit(f,superuser,201);
        mvc.perform(get("/api/projects/"+f.project.getId()+"/invitations").with(user(superuser.getId().toString()))).andExpect(status().isOk());
        mvc.perform(delete("/api/projects/"+f.project.getId()+"/members/"+member.getId()).with(user(superuser.getId().toString()))).andExpect(status().isNoContent());
    }
    @Test void invitationsCanBeRejectedRevokedOrExpireAndDoNotGrantMembership() throws Exception {
        var f=fixture();var recipient=account();UUID id=invite(f,recipient,"VIEWER");
        mvc.perform(post("/api/project-invitations/"+id+"/reject").with(user(recipient.getId().toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));accept(id,recipient,409);
        id=invite(f,recipient,"VIEWER");
        mvc.perform(post("/api/projects/"+f.project.getId()+"/invitations/"+id+"/revoke").with(user(f.owner.getId().toString()))).andExpect(status().isOk());accept(id,recipient,409);
        var expired=invitations.saveAndFlush(new ProjectInvitation(f.project,f.owner,recipient.getEmail(),ProjectInvitationRole.VIEWER,UUID.randomUUID().toString(),Instant.now().minusSeconds(10)));
        accept(expired.getId(),recipient,409);assertTrue(members.findByProjectIdAndUserId(f.project.getId(),recipient.getId()).isEmpty());
    }
    private User account() { return users.saveAndFlush(new User(UUID.randomUUID()+"@test.local","hash","Sharing user")); }
    private Fixture fixture() {
        User owner=account();Project project=projects.saveAndFlush(new Project("Sharing "+UUID.randomUUID(),null));
        members.saveAndFlush(new ProjectMember(project,owner,ProjectMemberRole.OWNER));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner.getId().toString(),"",java.util.List.of()));
        UUID model=models.createModel(project.getId(),new CreateUmlModelCommand("Sharing Model",null)).id();
        UUID diagram=diagrams.createDiagram(model,new CreateDiagramRequest("Main",DiagramType.CLASS,null)).id();return new Fixture(owner,project,model,diagram);
    }
    private void visibility(Fixture f,User user,String visibility,int expected) throws Exception {
        mvc.perform(patch("/api/projects/"+f.project.getId()+"/visibility").with(user(user.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\""+visibility+"\"}")).andExpect(status().is(expected));
    }
    private UUID invite(Fixture f,User recipient,String role) throws Exception {
        String response=mvc.perform(post("/api/projects/"+f.project.getId()+"/invitations").with(user(f.owner.getId().toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+recipient.getEmail().toUpperCase(java.util.Locale.ROOT)+"\",\"role\":\""+role+"\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("tokenHash"));return UUID.fromString(json.readTree(response).path("id").asText());
    }
    private void accept(UUID id,User recipient,int expected) throws Exception { mvc.perform(post("/api/project-invitations/"+id+"/accept").with(user(recipient.getId().toString()))).andExpect(status().is(expected)); }
    private void edit(Fixture f,User actor,int expected) throws Exception { mvc.perform(post("/api/uml-models/"+f.model+"/classifiers").with(user(actor.getId().toString())).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"NewClass\",\"kind\":\"CLASS\",\"visibility\":\"PUBLIC\",\"isAbstract\":false,\"isActive\":false}")).andExpect(status().is(expected)); }
    private record Fixture(User owner,Project project,UUID model,UUID diagram) {}
}
