package com.casegen.ai.proposal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casegen.ai.message.domain.*;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.repository.*;
import com.casegen.ai.proposal.review.*;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.repository.*;
import com.casegen.project.domain.*;
import com.casegen.project.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@TestConstructor(autowireMode=TestConstructor.AutowireMode.ALL)
@EnabledIfEnvironmentVariable(named="DB_URL",matches="jdbc:postgresql:.*")
class AiProposalBatchCreationIntegrationTest {
    @MockitoBean AiProposalBatchGenerator generator;
    @MockitoSpyBean AiProposalDependencyRepository dependencySpy;
    private final UserRepository users;private final ProjectRepository projects;private final ProjectMemberRepository members;
    private final AiSessionRepository sessions;private final AiMessageRepository messages;private final UmlModelRepository models;
    private final UmlElementRepository elements;private final UmlClassifierRepository classifiers;
    private final AiProposalBatchCreationService creation;private final AiProposalBatchRepository batches;
    private final AiProposalRepository proposals;private final AiProposalDependencyRepository dependencies;
    private final JdbcTemplate jdbc;private final EntityManager entityManager;
    private final List<UUID> cleanupProjects=new ArrayList<>(),cleanupUsers=new ArrayList<>();

    AiProposalBatchCreationIntegrationTest(UserRepository users,ProjectRepository projects,ProjectMemberRepository members,
            AiSessionRepository sessions,AiMessageRepository messages,UmlModelRepository models,UmlElementRepository elements,
            UmlClassifierRepository classifiers,AiProposalBatchCreationService creation,AiProposalBatchRepository batches,
            AiProposalRepository proposals,AiProposalDependencyRepository dependencies,JdbcTemplate jdbc,EntityManager entityManager){this.users=users;this.projects=projects;
        this.members=members;this.sessions=sessions;this.messages=messages;this.models=models;this.elements=elements;
        this.classifiers=classifiers;this.creation=creation;this.batches=batches;this.proposals=proposals;this.dependencies=dependencies;this.jdbc=jdbc;this.entityManager=entityManager;}

    @AfterEach void clear(){SecurityContextHolder.clearContext();reset(generator,dependencySpy);for(UUID projectId:cleanupProjects){
        jdbc.update("DELETE FROM ai_proposal_dependencies WHERE proposal_id IN (SELECT id FROM ai_proposals WHERE project_id=?)",projectId);
        jdbc.update("DELETE FROM ai_proposals WHERE project_id=?",projectId);jdbc.update("DELETE FROM ai_proposal_batches WHERE project_id=?",projectId);
        jdbc.update("DELETE FROM ai_messages WHERE session_id IN (SELECT id FROM ai_sessions WHERE project_id=?)",projectId);
        jdbc.update("DELETE FROM ai_session_participants WHERE session_id IN (SELECT id FROM ai_sessions WHERE project_id=?)",projectId);
        jdbc.update("DELETE FROM ai_sessions WHERE project_id=?",projectId);
        jdbc.update("DELETE FROM uml_classifiers WHERE element_id IN (SELECT e.id FROM uml_elements e JOIN uml_models m ON m.id=e.model_id WHERE m.project_id=?)",projectId);
        jdbc.update("DELETE FROM uml_elements WHERE model_id IN (SELECT id FROM uml_models WHERE project_id=?)",projectId);
        jdbc.update("DELETE FROM uml_models WHERE project_id=?",projectId);jdbc.update("DELETE FROM project_members WHERE project_id=?",projectId);
        jdbc.update("DELETE FROM projects WHERE id=?",projectId);}for(UUID userId:cleanupUsers){jdbc.update("DELETE FROM users WHERE id=?",userId);}cleanupProjects.clear();cleanupUsers.clear();}

    @Test void integralReviewPersistsSixSchemaTwoProposalsAndDerivedDependenciesWithoutChangingUml(){
        Fixture f=fixture();UUID clientBatchId=UUID.randomUUID();when(generator.generate(any())).thenReturn(generated(f));
        CreatedAiProposalBatch created=creation.create(new CreateAiProposalBatchCommand(f.project.getId(),f.session.getId(),
                f.source.getId(),f.user.getId(),clientBatchId));

        entityManager.clear();
        AiProposalBatch reloaded=batches.findByProject_IdAndClientBatchId(f.project.getId(),clientBatchId).orElseThrow();
        assertEquals(f.project.getId(),reloaded.getProject().getId());assertEquals(f.source.getId(),reloaded.getSourceMessage().getId());
        assertEquals(f.model.getId(),reloaded.getModel().getId());assertEquals("Inventario",reloaded.getTitle());
        assertEquals("Review integral",reloaded.getSummary());assertEquals("GEMINI",reloaded.getProvider());
        assertEquals(AiSkill.UML_REVIEWER,reloaded.getSkill());assertEquals(clientBatchId,reloaded.getClientBatchId());

        assertFalse(created.replayed());List<AiProposal> children=proposals.findByBatch_IdOrderByBatchOrderAsc(created.batch().getId());
        assertEquals(6,children.size());assertTrue(children.stream().allMatch(p->p.getStatus()==AiProposalStatus.PENDING));
        assertTrue(children.stream().allMatch(p->p.getPayloadSchemaVersion()==2));
        assertEquals(6,children.stream().map(AiProposal::getClientRef).distinct().count());
        assertEquals(6,children.stream().map(AiProposal::getBatchOrder).distinct().count());
        Map<String,AiProposal> byRef=new HashMap<>();children.forEach(p->byRef.put(p.getClientRef(),p));
        List<AiProposalDependency> rows=dependencies.findByBatchId(created.batch().getId());
        assertEquals(2,rows.size());assertDepends(rows,byRef,"rel-mov-detalle","detalle-movimiento");
        assertDepends(rows,byRef,"rel-detalle-producto","detalle-movimiento");
        assertTrue(byRef.get("detalle-movimiento").getBatchOrder()<byRef.get("rel-mov-detalle").getBatchOrder());
        assertTrue(byRef.get("detalle-movimiento").getBatchOrder()<byRef.get("rel-detalle-producto").getBatchOrder());
        assertEquals(created.batch().getBaseModelRevision(),created.batch().getModelRevisionCursor());
        assertTrue(classifiers.findByElementModelIdOrderByElementNameAsc(f.model.getId()).stream()
                .noneMatch(c->c.getElement().getName().equals("DetalleMovimiento")));

        CreatedAiProposalBatch replay=creation.create(new CreateAiProposalBatchCommand(f.project.getId(),f.session.getId(),
                f.source.getId(),f.user.getId(),clientBatchId));
        assertTrue(replay.replayed());assertEquals(created.batch().getId(),replay.batch().getId());
        verify(generator,times(1)).generate(any());assertEquals(6,proposals.findByBatch_IdOrderByBatchOrderAsc(created.batch().getId()).size());
    }

    @Test void failureAfterChildrenFlushRollsBackBatchChildrenAndDependencies(){Fixture f=fixture();UUID clientBatchId=UUID.randomUUID();when(generator.generate(any())).thenReturn(generated(f));
        doThrow(new RuntimeException("test fault after children flush")).when(dependencySpy).saveAll(any(Iterable.class));
        assertThrows(RuntimeException.class,()->creation.create(new CreateAiProposalBatchCommand(f.project.getId(),f.session.getId(),f.source.getId(),f.user.getId(),clientBatchId)));
        entityManager.clear();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ai_proposal_batches WHERE project_id=? AND client_batch_id=?",Integer.class,f.project.getId(),clientBatchId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ai_proposals WHERE project_id=? AND client_ref IN ('detalle-movimiento','producto-sku','movimiento-fecha','rel-mov-detalle','rel-detalle-producto','rel-mov-almacen')",Integer.class,f.project.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ai_proposal_dependencies d JOIN ai_proposals p ON p.id=d.proposal_id WHERE p.project_id=?",Integer.class,f.project.getId()));
        assertEquals(0,models.findById(f.model.getId()).orElseThrow().getRevision());
        assertEquals(6,classifiers.findByElementModelIdOrderByElementNameAsc(f.model.getId()).size());
    }

    @Test void concurrentCreatesReachDatabaseUniqueDefenseAndReturnSingleBatch() throws Exception {Fixture f=fixture();UUID clientBatchId=UUID.randomUUID();
        CyclicBarrier providerBarrier=new CyclicBarrier(2);GeneratedAiReview review=generated(f);when(generator.generate(any())).thenAnswer(invocation->{providerBarrier.await(15,TimeUnit.SECONDS);return review;});
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<CreatedAiProposalBatch> call=()->{SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(f.user.getId().toString(),"",List.of()));ready.countDown();start.await(15,TimeUnit.SECONDS);try{return creation.create(new CreateAiProposalBatchCommand(f.project.getId(),f.session.getId(),f.source.getId(),f.user.getId(),clientBatchId));}finally{SecurityContextHolder.clearContext();}};
        try{Future<CreatedAiProposalBatch> first=executor.submit(call),second=executor.submit(call);assertTrue(ready.await(15,TimeUnit.SECONDS));start.countDown();
            CreatedAiProposalBatch a=first.get(30,TimeUnit.SECONDS),b=second.get(30,TimeUnit.SECONDS);assertEquals(a.batch().getId(),b.batch().getId());assertTrue(a.replayed()||b.replayed());
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS));}
        entityManager.clear();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ai_proposal_batches WHERE project_id=? AND client_batch_id=?",Integer.class,f.project.getId(),clientBatchId));
        UUID batchId=batches.findByProject_IdAndClientBatchId(f.project.getId(),clientBatchId).orElseThrow().getId();
        assertEquals(6,proposals.findByBatch_IdOrderByBatchOrderAsc(batchId).size());assertEquals(2,dependencies.findByBatchId(batchId).size());verify(generator,times(2)).generate(any());
    }

    private void assertDepends(List<AiProposalDependency> rows,Map<String,AiProposal> byRef,String child,String parent){
        assertTrue(rows.stream().anyMatch(d->d.getProposal().getId().equals(byRef.get(child).getId())&&d.getDependsOn().getId().equals(byRef.get(parent).getId())));}
    private GeneratedAiReview generated(Fixture f){List<AiReviewItem> items=List.of(
        item("detalle-movimiento",AiReviewProposalType.ADD_CLASSIFIER,new ReviewAddClassifierPayload("DetalleMovimiento",null,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false)),
        item("producto-sku",AiReviewProposalType.ADD_ATTRIBUTE,attribute(f.ids.get("Producto"),"sku",PrimitiveType.STRING)),
        item("movimiento-fecha",AiReviewProposalType.ADD_ATTRIBUTE,attribute(f.ids.get("MovimientoInventario"),"fecha",PrimitiveType.STRING)),
        item("rel-mov-detalle",AiReviewProposalType.ADD_ASSOCIATION,association(new ReviewElementRef(f.ids.get("MovimientoInventario"),null),new ReviewElementRef(null,"detalle-movimiento"))),
        item("rel-detalle-producto",AiReviewProposalType.ADD_ASSOCIATION,association(new ReviewElementRef(null,"detalle-movimiento"),new ReviewElementRef(f.ids.get("Producto"),null))),
        item("rel-mov-almacen",AiReviewProposalType.ADD_ASSOCIATION,association(new ReviewElementRef(f.ids.get("MovimientoInventario"),null),new ReviewElementRef(f.ids.get("Almacen"),null))));
        return new GeneratedAiReview(new AiReviewResult("Inventario","Review integral",items,"GEMINI"),f.model.getRevision(),null,f.model.getId(),null,f.source.getId());}
    private AiReviewItem item(String ref,AiReviewProposalType type,AiReviewPayload payload){return new AiReviewItem(ref,type,ref,"reason",List.of(),payload);}
    private ReviewAddAttributePayload attribute(UUID owner,String name,PrimitiveType primitive){return new ReviewAddAttributePayload(new ReviewElementRef(owner,null),name,null,Visibility.PRIVATE,TypeKind.PRIMITIVE,primitive,null,0,1,false,false,false);}
    private ReviewAddAssociationPayload association(ReviewElementRef first,ReviewElementRef second){return new ReviewAddAssociationPayload(null,null,null,
            new ReviewAssociationEndPayload(first,null,0,null,true,AggregationKind.NONE,AssociationEndOwnership.ASSOCIATION),
            new ReviewAssociationEndPayload(second,null,0,null,true,AggregationKind.NONE,AssociationEndOwnership.ASSOCIATION));}
    private Fixture fixture(){String suffix=UUID.randomUUID().toString();User user=users.saveAndFlush(new User(suffix+"@test.local","hash","Owner"));cleanupUsers.add(user.getId());
        Project project=projects.saveAndFlush(new Project("Review "+suffix,null));members.saveAndFlush(new ProjectMember(project,user,ProjectMemberRole.OWNER));
        cleanupProjects.add(project.getId());
        AiSession session=sessions.saveAndFlush(new AiSession(project,user,Instant.now()));UmlModel model=models.saveAndFlush(new UmlModel(project,"Inventory "+suffix,null,user));
        Map<String,UUID> ids=new HashMap<>();for(String name:List.of("Producto","Almacen","Inventario","Proveedor","Bodega","MovimientoInventario")){
            UmlElement element=elements.saveAndFlush(new UmlElement(model,UmlElementType.CLASSIFIER,name,null));classifiers.saveAndFlush(new UmlClassifier(element,null,ClassifierKind.CLASS,Visibility.PUBLIC,false,false));ids.put(name,element.getId());}
        AiMessage question=messages.saveAndFlush(AiMessage.userMessage(session,user,"Review",model,null,UUID.randomUUID(),AiSkill.UML_REVIEWER,Instant.now()));
        AiMessage source=messages.saveAndFlush(AiMessage.assistantMessage(session,"Review result","GEMINI",model,null,question,Instant.now()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId().toString(),"",List.of()));return new Fixture(user,project,session,model,source,ids);}
    private record Fixture(User user,Project project,AiSession session,UmlModel model,AiMessage source,Map<String,UUID> ids){}
}
