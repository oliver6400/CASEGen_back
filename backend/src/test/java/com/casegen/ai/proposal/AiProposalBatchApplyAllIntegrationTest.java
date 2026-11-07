package com.casegen.ai.proposal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casegen.ai.message.domain.*;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.proposal.review.*;
import com.casegen.ai.proposal.service.*;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.repository.*;
import com.casegen.project.domain.*;
import com.casegen.project.repository.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
class AiProposalBatchApplyAllIntegrationTest {
    @MockitoBean AiProposalBatchGenerator generator;
    @MockitoSpyBean AiProposalBatchChildApplicator applicator;
    private final UserRepository users; private final ProjectRepository projects;
    private final ProjectMemberRepository members; private final AiSessionRepository sessions;
    private final AiMessageRepository messages; private final UmlModelRepository models;
    private final UmlElementRepository elements; private final UmlClassifierRepository classifiers;
    private final AiProposalBatchCreationService creation; private final AiProposalBatchApplyAllService applyAll;
    private final AiProposalDecisionService decisions;
    private final AiProposalBatchRepository batches; private final AiProposalRepository proposals;
    private final JdbcTemplate jdbc; private final EntityManager entityManager;
    private Fixture fixture;

    AiProposalBatchApplyAllIntegrationTest(UserRepository users, ProjectRepository projects,
            ProjectMemberRepository members, AiSessionRepository sessions,
            AiMessageRepository messages, UmlModelRepository models,
            UmlElementRepository elements, UmlClassifierRepository classifiers,
            AiProposalBatchCreationService creation, AiProposalBatchApplyAllService applyAll,
            AiProposalDecisionService decisions,
            AiProposalBatchRepository batches, AiProposalRepository proposals,
            JdbcTemplate jdbc, EntityManager entityManager) {
        this.users=users; this.projects=projects; this.members=members; this.sessions=sessions;
        this.messages=messages; this.models=models; this.elements=elements; this.classifiers=classifiers;
        this.creation=creation; this.applyAll=applyAll; this.decisions=decisions; this.batches=batches; this.proposals=proposals;
        this.jdbc=jdbc; this.entityManager=entityManager;
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext(); reset(generator, applicator);
        if (fixture == null) return;
        UUID p=fixture.project().getId(), m=fixture.model().getId();
        jdbc.update("DELETE FROM ai_proposal_dependencies WHERE proposal_id IN (SELECT id FROM ai_proposals WHERE project_id=?)",p);
        jdbc.update("DELETE FROM ai_proposals WHERE project_id=?",p);
        jdbc.update("DELETE FROM ai_proposal_batches WHERE project_id=?",p);
        jdbc.update("DELETE FROM diagram_edges WHERE diagram_id IN (SELECT id FROM diagrams WHERE model_id=?)",m);
        jdbc.update("DELETE FROM diagram_nodes WHERE diagram_id IN (SELECT id FROM diagrams WHERE model_id=?)",m);
        jdbc.update("DELETE FROM diagrams WHERE model_id=?",m);
        jdbc.update("DELETE FROM uml_association_ends WHERE association_id IN (SELECT id FROM uml_elements WHERE model_id=?)",m);
        for(String table:List.of("uml_associations","uml_generalizations","uml_realizations","uml_parameters","uml_properties","uml_operations","uml_classifiers","uml_packages"))
            jdbc.update("DELETE FROM "+table+" WHERE element_id IN (SELECT id FROM uml_elements WHERE model_id=?)",m);
        jdbc.update("DELETE FROM uml_elements WHERE model_id=?",m);
        jdbc.update("DELETE FROM ai_messages WHERE session_id IN (SELECT id FROM ai_sessions WHERE project_id=?)",p);
        jdbc.update("DELETE FROM ai_session_participants WHERE session_id IN (SELECT id FROM ai_sessions WHERE project_id=?)",p);
        jdbc.update("DELETE FROM ai_sessions WHERE project_id=?",p);
        jdbc.update("DELETE FROM uml_models WHERE id=?",m);
        jdbc.update("DELETE FROM project_members WHERE project_id=?",p);
        jdbc.update("DELETE FROM projects WHERE id=?",p);
        jdbc.update("DELETE FROM users WHERE id=?",fixture.user().getId());
    }

    @Test void integralSixChildBatchCommitsPhysicalUmlAndAudit() {
        CreatedAiProposalBatch created=createBatch(); long initial=fixture.model().getRevision();
        var result=applyAll.apply(command(created.batch().getId())); entityManager.clear();
        List<AiProposal> children=proposals.findByBatch_IdOrderByBatchOrderAsc(created.batch().getId());
        assertThat(children).hasSize(6).allMatch(p->p.getStatus()==AiProposalStatus.APPLIED)
                .allMatch(p->p.getAppliedElementId()!=null).allMatch(p->p.getAppliedModelRevision()!=null);
        assertThat(countElement("DetalleMovimiento")).isOne();
        assertThat(countElement("sku")).isOne(); assertThat(countElement("fecha")).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uml_associations a JOIN uml_elements e ON e.id=a.element_id WHERE e.model_id=?",Integer.class,fixture.model().getId())).isEqualTo(3);
        UmlModel model=models.findById(fixture.model().getId()).orElseThrow();
        AiProposalBatch batch=batches.findById(created.batch().getId()).orElseThrow();
        assertThat(model.getRevision()).isEqualTo(initial+6).isEqualTo(result.finalModelRevision());
        assertThat(batch.getModelRevisionCursor()).isEqualTo(model.getRevision());
        assertThat(batch.getAppliedBy()).isNotNull(); assertThat(batch.getAppliedAt()).isNotNull();
        verify(generator, times(1)).generate(any());
        verifyNoMoreInteractions(generator);
    }

    @Test void failureAfterThreeRealMutationsRollsBackEveryPhysicalChange() {
        CreatedAiProposalBatch created=createBatch(); long initial=fixture.model().getRevision();
        AtomicInteger calls=new AtomicInteger();
        doAnswer(invocation->{if(calls.incrementAndGet()==4)throw new RuntimeException("fault after three children");return invocation.callRealMethod();})
                .when(applicator).apply(any(),any(),anyList(),any(),any(),any());
        assertThatThrownBy(()->applyAll.apply(command(created.batch().getId())))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("three children");
        assertThat(calls).hasValue(4); entityManager.clear();
        assertThat(proposals.findByBatch_IdOrderByBatchOrderAsc(created.batch().getId()))
                .allMatch(p->p.getStatus()==AiProposalStatus.PENDING)
                .allMatch(p->p.getDecidedAt()==null).allMatch(p->p.getAppliedElementId()==null);
        assertThat(countElement("DetalleMovimiento")).isZero();
        assertThat(countElement("sku")).isZero(); assertThat(countElement("fecha")).isZero();
        assertThat(models.findById(fixture.model().getId()).orElseThrow().getRevision()).isEqualTo(initial);
        AiProposalBatch batch=batches.findById(created.batch().getId()).orElseThrow();
        assertThat(batch.getModelRevisionCursor()).isEqualTo(initial);
        assertThat(batch.getAppliedBy()).isNull(); assertThat(batch.getAppliedAt()).isNull();
    }

    @Test void twoConcurrentApplyAllCallsSerializeWithoutDuplicateMutations() throws Exception {
        CreatedAiProposalBatch created=createBatch(); UUID batchId=created.batch().getId();
        ExecutorService executor=Executors.newFixedThreadPool(2); CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<Object> task=()->{authenticate();ready.countDown();start.await(15,TimeUnit.SECONDS);try{return applyAll.apply(command(batchId));}catch(Throwable t){return t;}finally{SecurityContextHolder.clearContext();}};
        Object a,b; try{Future<Object> fa=executor.submit(task),fb=executor.submit(task);assertThat(ready.await(15,TimeUnit.SECONDS)).isTrue();start.countDown();a=fa.get(45,TimeUnit.SECONDS);b=fb.get(45,TimeUnit.SECONDS);}finally{executor.shutdownNow();executor.awaitTermination(15,TimeUnit.SECONDS);}
        assertThat(List.of(a,b).stream().filter(AiProposalBatchApplyAllResult.class::isInstance).count()).isOne();
        assertThat(List.of(a,b).stream().filter(x->x instanceof AiProposalException e&&e.getCode()==AiProposalErrorCode.BATCH_ALREADY_DECIDED).count()).isOne();
        entityManager.clear(); assertThat(countElement("DetalleMovimiento")).isOne();assertThat(countElement("sku")).isOne();assertThat(countElement("fecha")).isOne();
        assertThat(proposals.findByBatch_IdOrderByBatchOrderAsc(batchId)).allMatch(p->p.getStatus()==AiProposalStatus.APPLIED);
    }

    @Test void partiallyAppliedBatchContinuesWithoutDuplicatingClassifier() {
        CreatedAiProposalBatch created=createBatch();
        AiProposal first=proposals.findByBatch_IdOrderByBatchOrderAsc(created.batch().getId()).getFirst();
        decisions.apply(decision(first.getId()));

        var result=applyAll.apply(command(created.batch().getId())); entityManager.clear();

        assertThat(result.appliedCount()).isEqualTo(5);
        assertThat(countElement("DetalleMovimiento")).isOne();
        assertThat(proposals.findByBatch_IdOrderByBatchOrderAsc(created.batch().getId()))
                .hasSize(6).allMatch(p->p.getStatus()==AiProposalStatus.APPLIED);
    }

    @Test void concurrentApplyAllAndIndividualApplyHaveNoDuplicateOrDeadlock() throws Exception {
        CreatedAiProposalBatch created=createBatch(); UUID batchId=created.batch().getId();
        UUID childId=proposals.findByBatch_IdOrderByBatchOrderAsc(batchId).getFirst().getId();

        List<Object> outcomes=race(
                ()->applyAll.apply(command(batchId)),
                ()->decisions.apply(decision(childId)));

        assertThat(outcomes).anyMatch(AiProposalBatchApplyAllResult.class::isInstance);
        entityManager.clear();
        assertThat(countElement("DetalleMovimiento")).isOne();
        assertThat(proposals.findByBatch_IdOrderByBatchOrderAsc(batchId))
                .hasSize(6).allMatch(p->p.getStatus()==AiProposalStatus.APPLIED);
    }

    @Test void concurrentApplyAllAndRejectReachOneCoherentDecisionWithoutDeadlock() throws Exception {
        CreatedAiProposalBatch created=createBatch(); UUID batchId=created.batch().getId();
        UUID independentChild=proposals.findByBatch_IdOrderByBatchOrderAsc(batchId).get(1).getId();

        race(() -> applyAll.apply(command(batchId)), () -> decisions.reject(decision(independentChild)));

        entityManager.clear();
        List<AiProposal> children=proposals.findByBatch_IdOrderByBatchOrderAsc(batchId);
        assertThat(children).hasSize(6).allMatch(p->p.getStatus()!=AiProposalStatus.PENDING);
        assertThat(children.stream().filter(p->p.getId().equals(independentChild)).findFirst().orElseThrow().getStatus())
                .isIn(AiProposalStatus.APPLIED,AiProposalStatus.REJECTED);
        assertThat(countElement("DetalleMovimiento")).isOne();
        assertThat(countElement("sku")).isLessThanOrEqualTo(1);
    }

    private CreatedAiProposalBatch createBatch(){fixture=fixture();when(generator.generate(any())).thenReturn(generated());return creation.create(new CreateAiProposalBatchCommand(fixture.project().getId(),fixture.session().getId(),fixture.source().getId(),fixture.user().getId(),UUID.randomUUID()));}
    private AiProposalBatchApplyAllCommand command(UUID batchId){return new AiProposalBatchApplyAllCommand(fixture.project().getId(),batchId,fixture.user().getId(),"approved");}
    private AiProposalDecisionCommand decision(UUID proposalId){return new AiProposalDecisionCommand(fixture.project().getId(),proposalId,fixture.user().getId(),"approved");}
    private List<Object> race(Callable<Object> first,Callable<Object> second) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<Object> wrapFirst=()->raceCall(first,ready,start),wrapSecond=()->raceCall(second,ready,start);
        try{Future<Object> a=executor.submit(wrapFirst),b=executor.submit(wrapSecond);assertThat(ready.await(15,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS));}
        finally{executor.shutdownNow();executor.awaitTermination(15,TimeUnit.SECONDS);}
    }
    private Object raceCall(Callable<Object> operation,CountDownLatch ready,CountDownLatch start) throws Exception {authenticate();ready.countDown();start.await(15,TimeUnit.SECONDS);try{return operation.call();}catch(Throwable failure){return failure;}finally{SecurityContextHolder.clearContext();}}
    private int countElement(String name){return jdbc.queryForObject("SELECT count(*) FROM uml_elements WHERE model_id=? AND name=?",Integer.class,fixture.model().getId(),name);}
    private void authenticate(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(fixture.user().getId().toString(),"",List.of()));}
    private GeneratedAiReview generated(){var f=fixture;List<AiReviewItem> items=List.of(item("detalle-movimiento",AiReviewProposalType.ADD_CLASSIFIER,new ReviewAddClassifierPayload("DetalleMovimiento",null,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false)),item("producto-sku",AiReviewProposalType.ADD_ATTRIBUTE,attribute(f.ids().get("Producto"),"sku")),item("movimiento-fecha",AiReviewProposalType.ADD_ATTRIBUTE,attribute(f.ids().get("MovimientoInventario"),"fecha")),item("rel-mov-detalle",AiReviewProposalType.ADD_ASSOCIATION,association(new ReviewElementRef(f.ids().get("MovimientoInventario"),null),new ReviewElementRef(null,"detalle-movimiento"))),item("rel-detalle-producto",AiReviewProposalType.ADD_ASSOCIATION,association(new ReviewElementRef(null,"detalle-movimiento"),new ReviewElementRef(f.ids().get("Producto"),null))),item("rel-mov-almacen",AiReviewProposalType.ADD_ASSOCIATION,association(new ReviewElementRef(f.ids().get("MovimientoInventario"),null),new ReviewElementRef(f.ids().get("Almacen"),null))));return new GeneratedAiReview(new AiReviewResult("Inventario","Review integral",items,"GEMINI"),f.model().getRevision(),null,f.model().getId(),null,f.source().getId());}
    private AiReviewItem item(String r,AiReviewProposalType t,AiReviewPayload p){return new AiReviewItem(r,t,r,"reason",List.of(),p);}
    private ReviewAddAttributePayload attribute(UUID owner,String name){return new ReviewAddAttributePayload(new ReviewElementRef(owner,null),name,null,Visibility.PRIVATE,TypeKind.PRIMITIVE,PrimitiveType.STRING,null,0,1,false,false,false);}
    private ReviewAddAssociationPayload association(ReviewElementRef a,ReviewElementRef b){return new ReviewAddAssociationPayload(null,null,null,new ReviewAssociationEndPayload(a,null,0,null,true,AggregationKind.NONE,AssociationEndOwnership.ASSOCIATION),new ReviewAssociationEndPayload(b,null,0,null,true,AggregationKind.NONE,AssociationEndOwnership.ASSOCIATION));}
    private Fixture fixture(){String s=UUID.randomUUID().toString();User u=users.saveAndFlush(new User(s+"@test.local","hash","Owner"));Project p=projects.saveAndFlush(new Project("ApplyAll "+s,null));members.saveAndFlush(new ProjectMember(p,u,ProjectMemberRole.OWNER));AiSession session=sessions.saveAndFlush(new AiSession(p,u,Instant.now()));UmlModel m=models.saveAndFlush(new UmlModel(p,"Inventory "+s,null,u));Map<String,UUID> ids=new HashMap<>();for(String n:List.of("Producto","Almacen","Inventario","Proveedor","Bodega","MovimientoInventario")){UmlElement e=elements.saveAndFlush(new UmlElement(m,UmlElementType.CLASSIFIER,n,null));classifiers.saveAndFlush(new UmlClassifier(e,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false));ids.put(n,e.getId());}AiMessage q=messages.saveAndFlush(AiMessage.userMessage(session,u,"Review",m,null,UUID.randomUUID(),AiSkill.UML_REVIEWER,Instant.now()));AiMessage source=messages.saveAndFlush(AiMessage.assistantMessage(session,"Review result","GEMINI",m,null,q,Instant.now()));Fixture f=new Fixture(u,p,session,m,source,ids);this.fixture=f;authenticate();return f;}
    private record Fixture(User user,Project project,AiSession session,UmlModel model,AiMessage source,Map<String,UUID> ids){}
}
