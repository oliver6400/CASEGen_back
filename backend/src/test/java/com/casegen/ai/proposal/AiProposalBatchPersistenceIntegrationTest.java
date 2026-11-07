package com.casegen.ai.proposal;

import static org.junit.jupiter.api.Assertions.*;

import com.casegen.ai.message.domain.*;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.repository.*;
import com.casegen.project.domain.Project;
import com.casegen.project.repository.ProjectRepository;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
class AiProposalBatchPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private final UserRepository users; private final ProjectRepository projects;
    private final AiSessionRepository sessions; private final AiMessageRepository messages;
    private final UmlModelRepository models; private final DiagramRepository diagrams;
    private final AiProposalBatchRepository batches; private final AiProposalRepository proposals;
    private final AiProposalDependencyRepository dependencies; private final JdbcTemplate jdbc;

    AiProposalBatchPersistenceIntegrationTest(UserRepository users, ProjectRepository projects,
            AiSessionRepository sessions, AiMessageRepository messages, UmlModelRepository models,
            DiagramRepository diagrams, AiProposalBatchRepository batches, AiProposalRepository proposals,
            AiProposalDependencyRepository dependencies, JdbcTemplate jdbc) {
        this.users=users; this.projects=projects; this.sessions=sessions; this.messages=messages;
        this.models=models; this.diagrams=diagrams; this.batches=batches; this.proposals=proposals;
        this.dependencies=dependencies; this.jdbc=jdbc;
    }

    @Test void v19SchemaExistsAndIsCurrent() {
        assertEquals("19", jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=true ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(1, countTable("ai_proposal_batches"));
        assertEquals(1, countTable("ai_proposal_dependencies"));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='ai_proposals' AND column_name IN ('batch_id','batch_order','client_ref')", Integer.class));
    }

    @Test void v19PersistsBothUpdateTypesAsPendingSchemaTwoChildren() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        AiProposal classifier=proposals.saveAndFlush(child(batch,1,"update-classifier",AiProposalType.UPDATE_CLASSIFIER));
        AiProposal attribute=proposals.saveAndFlush(child(batch,2,"update-attribute",AiProposalType.UPDATE_ATTRIBUTE));
        proposals.flush();
        Map<String,Object> classifierRow=jdbc.queryForMap("SELECT proposal_type,status,payload_schema_version,batch_id,client_ref,payload FROM ai_proposals WHERE id=?",classifier.getId());
        Map<String,Object> attributeRow=jdbc.queryForMap("SELECT proposal_type,status,payload_schema_version,batch_id,client_ref,payload FROM ai_proposals WHERE id=?",attribute.getId());
        assertEquals("UPDATE_CLASSIFIER",classifierRow.get("proposal_type"));
        assertEquals("UPDATE_ATTRIBUTE",attributeRow.get("proposal_type"));
        assertEquals("PENDING",classifierRow.get("status"));assertEquals("PENDING",attributeRow.get("status"));
        assertEquals(2,((Number)classifierRow.get("payload_schema_version")).intValue());
        assertEquals(2,((Number)attributeRow.get("payload_schema_version")).intValue());
        assertEquals(batch.getId(),classifierRow.get("batch_id"));assertEquals(batch.getId(),attributeRow.get("batch_id"));
        assertNotNull(classifierRow.get("payload"));assertNotNull(attributeRow.get("payload"));
    }

    @Test void v19ConstraintRejectsUnknownProposalType() {
        Fixture f=fixture(false); AiProposal proposal=proposals.saveAndFlush(individual(f));
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET proposal_type='UNKNOWN_TYPE' WHERE id=?",proposal.getId()));
    }

    @Test void individualProposalRemainsCompatibleAndHasNullBatchShape() {
        Fixture f=fixture(true); AiProposal proposal=proposals.saveAndFlush(individual(f));
        Map<String,Object> shape=jdbc.queryForMap("SELECT batch_id,batch_order,client_ref FROM ai_proposals WHERE id=?",proposal.getId());
        assertTrue(shape.values().stream().allMatch(java.util.Objects::isNull));
    }

    @Test void persistsBatchChildrenDependenciesAndRepositoryQueries() throws Exception {
        Fixture f=fixture(true); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        AiProposal first=proposals.saveAndFlush(child(batch,1,"producto"));
        AiProposal second=proposals.saveAndFlush(child(batch,2,"detalle"));
        dependencies.saveAndFlush(AiProposalDependency.create(second,first));
        assertEquals(2,proposals.findByBatch_IdOrderByBatchOrderAsc(batch.getId()).size());
        assertEquals(1,dependencies.findByProposal_Id(second.getId()).size());
        assertEquals(1,dependencies.findByBatchId(batch.getId()).size());
        assertEquals(batch.getId(),batches.findByProject_IdAndClientBatchId(f.project.getId(),batch.getClientBatchId()).orElseThrow().getId());
        assertEquals(1,batches.findByProject_IdOrderByCreatedAtDescIdDesc(f.project.getId(),PageRequest.of(0,10)).getTotalElements());
        assertEquals(batch.getId(),batches.findForUpdate(f.project.getId(),batch.getId()).orElseThrow().getId());
        Method method=AiProposalBatchRepository.class.getMethod("findForUpdate",UUID.class,UUID.class);
        assertEquals(LockModeType.PESSIMISTIC_WRITE,method.getAnnotation(Lock.class).value());
    }

    @Test void batchClientIdIsUniqueInsideProject() {
        Fixture f=fixture(false); UUID client=UUID.randomUUID(); batches.saveAndFlush(batch(f,client));
        assertConstraint(() -> batches.saveAndFlush(batch(f,client)));
    }

    @Test void childOrderAndRefAreUniqueInsideBatch() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        proposals.saveAndFlush(child(batch,1,"one"));
        assertConstraint(() -> proposals.saveAndFlush(child(batch,1,"two")));
    }

    @Test void duplicateClientRefIsRejected() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        proposals.saveAndFlush(child(batch,1,"same"));
        assertConstraint(() -> proposals.saveAndFlush(child(batch,2,"same")));
    }

    @Test void databaseRejectsInvalidBatchShapeAndOrder() {
        Fixture f=fixture(false); AiProposal individual=proposals.saveAndFlush(individual(f));
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET client_ref='orphan' WHERE id=?",individual.getId()));
    }

    @Test void databaseRejectsOrderAboveFifteen() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        AiProposal child=proposals.saveAndFlush(child(batch,1,"child"));
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET batch_order=16 WHERE id=?",child.getId()));
    }

    @Test void databaseRejectsChildWithoutOrder() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        AiProposal child=proposals.saveAndFlush(child(batch,1,"child"));
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET batch_order=NULL WHERE id=?",child.getId()));
    }

    @Test void dependencyPrimaryKeySelfCheckAndForeignKeysAreEnforced() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        AiProposal first=proposals.saveAndFlush(child(batch,1,"first"));
        AiProposal second=proposals.saveAndFlush(child(batch,2,"second"));
        dependencies.saveAndFlush(AiProposalDependency.create(second,first));
        assertConstraint(() -> jdbc.update("INSERT INTO ai_proposal_dependencies(proposal_id,depends_on_proposal_id) VALUES (?,?)",second.getId(),first.getId()));
    }

    @Test void databaseRejectsSelfDependency() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        AiProposal child=proposals.saveAndFlush(child(batch,1,"child"));
        assertConstraint(() -> jdbc.update("INSERT INTO ai_proposal_dependencies(proposal_id,depends_on_proposal_id) VALUES (?,?)",child.getId(),child.getId()));
    }

    @Test void dependencyForeignKeyIsEnforced() {
        Fixture f=fixture(false); AiProposalBatch batch=batches.saveAndFlush(batch(f,UUID.randomUUID()));
        AiProposal child=proposals.saveAndFlush(child(batch,1,"child"));
        assertConstraint(() -> jdbc.update("INSERT INTO ai_proposal_dependencies(proposal_id,depends_on_proposal_id) VALUES (?,?)",child.getId(),UUID.randomUUID()));
    }

    @Test void batchForeignKeyIsEnforced() {
        Fixture f=fixture(false); AiProposal proposal=proposals.saveAndFlush(individual(f));
        assertConstraint(() -> jdbc.update("UPDATE ai_proposals SET batch_id=?,batch_order=1,client_ref='x' WHERE id=?",UUID.randomUUID(),proposal.getId()));
    }

    private int countTable(String table){return jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",Integer.class,table);}
    private void assertConstraint(Runnable action){assertThrows(DataIntegrityViolationException.class,action::run);}
    private AiProposalBatch batch(Fixture f,UUID client){return AiProposalBatch.create(f.project,f.session,f.assistant,f.user,f.model,f.diagram,"Review","Summary",f.model.getRevision(),f.diagram==null?null:f.diagram.getRevision(),"GEMINI",client);}
    private AiProposal child(AiProposalBatch b,int order,String ref){return child(b,order,ref,AiProposalType.ADD_CLASSIFIER);}
    private AiProposal child(AiProposalBatch b,int order,String ref,AiProposalType type){return AiProposal.pendingInBatch(b,order,ref,type,"Title","Reason",JsonMapper.builder().build().valueToTree(Map.of("name",ref)),(short)(type.name().startsWith("UPDATE_")?2:1),UUID.randomUUID());}
    private AiProposal individual(Fixture f){return AiProposal.pending(f.project,f.session,f.assistant,f.user,f.model,f.diagram,AiProposalType.ADD_CLASSIFIER,"Title","Reason",JsonMapper.builder().build().valueToTree(Map.of("name","X")),(short)1,f.model.getRevision(),f.diagram==null?null:f.diagram.getRevision(),"GEMINI",UUID.randomUUID());}
    private Fixture fixture(boolean withDiagram){String suffix=UUID.randomUUID().toString();User user=users.saveAndFlush(new User(suffix+"@test.local","hash","Member"));Project project=projects.saveAndFlush(new Project("Batch "+suffix,null));AiSession session=sessions.saveAndFlush(new AiSession(project,user,NOW));UmlModel model=models.saveAndFlush(new UmlModel(project,"Model "+suffix,null,user));Diagram diagram=withDiagram?diagrams.saveAndFlush(new Diagram(model,null,"Diagram "+suffix,DiagramType.CLASS,user)):null;AiMessage question=messages.saveAndFlush(AiMessage.userMessage(session,user,"Review",model,diagram,UUID.randomUUID(),AiSkill.UML_REVIEWER,NOW));AiMessage assistant=messages.saveAndFlush(AiMessage.assistantMessage(session,"Review result","GEMINI",model,diagram,question,NOW.plusSeconds(1)));return new Fixture(user,project,session,model,diagram,assistant);}
    private record Fixture(User user,Project project,AiSession session,UmlModel model,Diagram diagram,AiMessage assistant){}
}
