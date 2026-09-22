package com.casegen.generation.normalization;

import static org.junit.jupiter.api.Assertions.*;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.generation.backend.service.GenerationService;
import com.casegen.generation.dto.ElementMappingRequest;
import com.casegen.generation.dto.GenerationProfileRequest;
import com.casegen.generation.logical.service.LogicalModelService;
import com.casegen.generation.normalization.service.NormalizationService;
import com.casegen.generation.service.GenerationProfileService;
import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.command.UmlAssociationEndCommand;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.validation.service.ValidationService;
import com.casegen.versioning.service.ModelVersionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NormalizationIntegrationTest {
    private final UserRepository users; private final ProjectRepository projects;
    private final ProjectMemberRepository members; private final UmlModelService models;
    private final UmlRelationshipService relationships; private final ValidationService validation;
    private final ModelVersionService versions; private final GenerationProfileService profiles;
    private final LogicalModelService logicalModels; private final NormalizationService normalization;
    private final GenerationService generation; private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    NormalizationIntegrationTest(UserRepository users, ProjectRepository projects, ProjectMemberRepository members,
            UmlModelService models, UmlRelationshipService relationships, ValidationService validation,
            ModelVersionService versions, GenerationProfileService profiles, LogicalModelService logicalModels,
            NormalizationService normalization, GenerationService generation, JdbcTemplate jdbc) {
        this.users=users;this.projects=projects;this.members=members;this.models=models;this.relationships=relationships;
        this.validation=validation;this.versions=versions;this.profiles=profiles;this.logicalModels=logicalModels;
        this.normalization=normalization;this.generation=generation;this.jdbc=jdbc;
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void warningIsPersistentDeterministicAndDoesNotChangeOrBlockLogicalModel() {
        User owner=users.saveAndFlush(new User(UUID.randomUUID()+"@test.local","hashed","Normalization Owner"));
        Project project=projects.saveAndFlush(new Project("Normalization "+UUID.randomUUID(),null));
        members.saveAndFlush(new ProjectMember(project,owner,ProjectMemberRole.OWNER)); authenticate(owner);
        UUID model=models.createModel(project.getId(),new CreateUmlModelCommand("Normalization model",null)).id();
        UUID customer=models.createClassifier(model,new CreateUmlClassifierCommand("Customer",null,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false)).id();
        UUID customerId=models.createProperty(model,customer,new CreateUmlPropertyCommand("id",null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.STRING,null,1,1,false,false,false)).id();
        UUID customerName=models.createProperty(model,customer,new CreateUmlPropertyCommand("name",null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.STRING,null,1,1,false,false,false)).id();
        UUID order=models.createClassifier(model,new CreateUmlClassifierCommand("PurchaseOrder",null,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false)).id();
        UUID orderId=models.createProperty(model,order,new CreateUmlPropertyCommand("id",null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.STRING,null,1,1,false,false,false)).id();
        relationships.createAssociation(model,new CreateUmlAssociationCommand(null,null,null,
                new UmlAssociationEndCommand("customer",customer,Visibility.PUBLIC,1,1,false,AggregationKind.NONE,AssociationEndOwnership.ASSOCIATION),
                new UmlAssociationEndCommand("orders",order,Visibility.PUBLIC,0,null,false,AggregationKind.NONE,AssociationEndOwnership.ASSOCIATION)));
        var profile=profiles.save(model,null,new GenerationProfileRequest("Normalization Backend",GenerationProfileRequest.Target.SPRING_BOOT,
                "com.test","normalization-api","com.test.normalization",21,"4.1.1",GenerationProfileRequest.Database.POSTGRESQL,null));
        profiles.saveMapping(model,profile.id(),null,new ElementMappingRequest(customer,ElementMappingRequest.TargetKind.ENTITY,json.valueToTree(Map.of("idPropertyId",customerId.toString()))));
        profiles.saveMapping(model,profile.id(),null,new ElementMappingRequest(order,ElementMappingRequest.TargetKind.ENTITY,json.valueToTree(Map.of("idPropertyId",orderId.toString()))));
        assertEquals("VALID",validation.validate(model).status());
        var logical=logicalModels.build(versions.create(model).id(),profile.id()); assertEquals("VALID",logical.status());
        UUID orderTable=jdbc.queryForObject("SELECT id FROM logical_tables WHERE logical_model_id=? AND name='purchase_order'",UUID.class,logical.id());
        jdbc.update("INSERT INTO logical_columns(id,table_id,source_property_id,name,sql_type,nullable,ordinal_position) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID(),orderTable,customerName,"customer_name","VARCHAR(255)",false,99);
        String before=jdbc.queryForObject("SELECT status||':'||checksum FROM logical_models WHERE id=?",String.class,logical.id());

        var first=normalization.analyze(logical.id()); var second=normalization.analyze(logical.id());
        assertEquals("WARNING",first.status()); assertEquals("2NF",first.highestNormalForm());
        assertEquals(first.status(),second.status()); assertEquals(first.highestNormalForm(),second.highestNormalForm());
        assertEquals(first.issues().stream().map(issue->List.of(issue.normalForm(),issue.code(),issue.message(),issue.recommendation(),issue.metadata())).toList(),
                second.issues().stream().map(issue->List.of(issue.normalForm(),issue.code(),issue.message(),issue.recommendation(),issue.metadata())).toList());
        assertEquals(2,normalization.list(logical.id()).size());
        var issue=first.issues().stream().filter(value->value.code().equals("KNOWN_TRANSITIVE_DEPENDENCY")).findFirst().orElseThrow();
        assertEquals(customerName.toString(),issue.sourceElementId()); assertNotNull(issue.logicalTableId()); assertNotNull(issue.logicalColumnId());
        assertEquals(before,jdbc.queryForObject("SELECT status||':'||checksum FROM logical_models WHERE id=?",String.class,logical.id()));

        var accepted=generation.generate(logical.id()); assertEquals("QUEUED",accepted.status());
        var generated=await(accepted.generationId()); assertEquals("SUCCESS",generated.status(),generated.errorMessage());
    }

    private com.casegen.generation.backend.dto.GenerationDtos.Response await(UUID id) {
        Instant deadline=Instant.now().plus(Duration.ofMinutes(5));
        while(Instant.now().isBefore(deadline)){var run=generation.get(id);if(run.status().equals("SUCCESS")||run.status().equals("FAILED"))return run;
            try{Thread.sleep(250);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}
        throw new AssertionError("Generation did not finish: "+id);
    }
    private void authenticate(User user) { SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user.getId().toString(),"",List.of())); }
}
