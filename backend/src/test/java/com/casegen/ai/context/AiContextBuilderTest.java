package com.casegen.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiPromptRole;
import com.casegen.ai.provider.AiProviderProperties;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.domain.AiSessionStatus;
import com.casegen.ai.session.repository.AiSessionRepository;
import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectVisibility;
import com.casegen.project.repository.ProjectRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class AiContextBuilderTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private ProjectRepository projects;
    private AiSessionRepository sessions;
    private UmlModelRepository models;
    private DiagramRepository diagrams;
    private AiMessageRepository messages;
    private UmlContextSummaryBuilder umlSummary;
    private Project project;
    private AiSession session;
    private AiContextBuilder builder;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        sessions = mock(AiSessionRepository.class);
        models = mock(UmlModelRepository.class);
        diagrams = mock(DiagramRepository.class);
        messages = mock(AiMessageRepository.class);
        umlSummary = mock(UmlContextSummaryBuilder.class);
        project = project(projectId, "Ventas", "Gestión comercial");
        session = session(sessionId, project, true);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(messages.findRecentByProjectId(eq(projectId), any())).thenReturn(List.of());
        builder = builder(30, 8000, 24000);
    }

    @Test
    void projectWithoutModelProducesSafePrompt() {
        AiPrompt prompt = builder.build(request(null, null, "¿Qué falta?", null));

        assertTrue(prompt.systemInstruction().contains("Nombre: Ventas"));
        assertTrue(prompt.systemInstruction().contains("JORNADA ACTUAL"));
        assertTrue(prompt.systemInstruction().contains("no modificas ni aplicas cambios"));
        assertTrue(prompt.systemInstruction().contains("No muestres razonamiento interno"));
        assertTrue(prompt.systemInstruction().contains("etiquetas <think>"));
        assertEquals(AiPromptRole.USER, prompt.messages().getLast().role());
        assertEquals("¿Qué falta?", prompt.messages().getLast().content());
    }

    @Test
    void modelAndDiagramAddOnlyExplicitContext() {
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        UmlModel model = model(modelId, project);
        Diagram diagram = diagram(diagramId, model, "Clases principales");
        when(models.findById(modelId)).thenReturn(Optional.of(model));
        when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
        when(umlSummary.build(model, diagram)).thenReturn("Clase Cliente\n- atributo nit: INTEGER");

        AiPrompt prompt = builder.build(request(modelId, diagramId, "Analiza", null));

        assertTrue(prompt.systemInstruction().contains("MODELO UML ACTUAL"));
        assertTrue(prompt.systemInstruction().contains("DIAGRAMA ACTUAL"));
        assertTrue(prompt.systemInstruction().contains("Clase Cliente"));
        assertFalse(prompt.systemInstruction().contains("viewport"));
        assertFalse(prompt.systemInstruction().contains("width"));
    }

    @Test
    void semanticInheritanceSummaryReachesFinalPrompt() {
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        UmlModel model = model(modelId, project);
        Diagram diagram = diagram(diagramId, model, "sprint_esp_pk - Importado");
        when(models.findById(modelId)).thenReturn(Optional.of(model));
        when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
        when(umlSummary.build(model, diagram)).thenReturn("""
                RELACIONES
                Tipo: GENERALIZATION | Subclase: Cliente | Superclase: Usuario | Significado: Cliente hereda de Usuario
                Tipo: GENERALIZATION | Subclase: Empleado | Superclase: Usuario | Significado: Empleado hereda de Usuario
                """);

        AiPrompt prompt = builder.build(request(modelId, diagramId,
                "¿Existe alguna relación de herencia?", null));

        assertTrue(prompt.systemInstruction().contains("Cliente hereda de Usuario"));
        assertTrue(prompt.systemInstruction().contains("Empleado hereda de Usuario"));
    }

    @Test
    void generalSkillAddsGeneralDevelopmentInstruction() {
        AiPrompt prompt = builder.build(request(null, null, "Resume", null, AiSkill.GENERAL));
        assertTrue(prompt.systemInstruction().contains("MODO GENERAL"));
        assertTrue(prompt.systemInstruction().contains("asistente general de desarrollo"));
    }

    @Test
    void umlReviewerAddsEvidenceAndYesNoRules() {
        AiPrompt prompt = builder.build(request(null, null, "Revisa UML", null,
                AiSkill.UML_REVIEWER));
        assertTrue(prompt.systemInstruction().contains("MODO REVISOR UML"));
        assertTrue(prompt.systemInstruction().contains("generalizaciones o herencia"));
        assertTrue(prompt.systemInstruction().contains("NO respondas únicamente Sí o No"));
        assertTrue(prompt.systemInstruction().contains("justificación breve usando nombres concretos"));
        assertTrue(prompt.systemInstruction().contains("Cliente y Empleado heredan de Usuario"));
        assertTrue(prompt.systemInstruction().contains("No solicites un segundo mensaje"));
        assertTrue(prompt.systemInstruction().contains("HECHO DEL MODELO"));
    }

    @Test
    void databaseReviewerAddsRelationalRules() {
        AiPrompt prompt = builder.build(request(null, null, "Revisa persistencia", null,
                AiSkill.DATABASE_REVIEWER));
        assertTrue(prompt.systemInstruction().contains("MODO REVISOR DE BASE DE DATOS"));
        assertTrue(prompt.systemInstruction().contains("claves primarias"));
        assertTrue(prompt.systemInstruction().contains("herencia UML a relacional"));
        assertTrue(prompt.systemInstruction().contains("lo que existe actualmente"));
    }

    @Test
    void rejectsSessionFromAnotherProject() {
        Project other = project(UUID.randomUUID(), "Otro", null);
        when(session.getProject()).thenReturn(other);
        assertCode(AiContextErrorCode.SESSION_PROJECT_MISMATCH,
                () -> builder.build(request(null, null, "Pregunta", null)));
    }

    @Test
    void rejectsClosedSession() {
        when(session.isActive()).thenReturn(false);
        when(session.getStatus()).thenReturn(AiSessionStatus.CLOSED);
        assertCode(AiContextErrorCode.SESSION_NOT_ACTIVE,
                () -> builder.build(request(null, null, "Pregunta", null)));
    }

    @Test
    void rejectsModelFromAnotherProject() {
        UUID modelId = UUID.randomUUID();
        Project other = project(UUID.randomUUID(), "Otro", null);
        UmlModel foreignModel = model(modelId, other);
        when(models.findById(modelId)).thenReturn(Optional.of(foreignModel));
        assertCode(AiContextErrorCode.MODEL_PROJECT_MISMATCH,
                () -> builder.build(request(modelId, null, "Pregunta", null)));
    }

    @Test
    void rejectsDiagramFromAnotherModel() {
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        UmlModel selected = model(modelId, project);
        UmlModel otherModel = model(UUID.randomUUID(), project);
        Diagram foreignDiagram = diagram(diagramId, otherModel, "Ajeno");
        when(models.findById(modelId)).thenReturn(Optional.of(selected));
        when(diagrams.findById(diagramId)).thenReturn(Optional.of(foreignDiagram));
        assertCode(AiContextErrorCode.DIAGRAM_MODEL_MISMATCH,
                () -> builder.build(request(modelId, diagramId, "Pregunta", null)));
    }

    @Test
    void historyMapsRolesAndReturnsChronologicalOrderAcrossWorkSessions() {
        AiMessage newest = message(AiMessageRole.USER, "mensaje jornada dos");
        AiMessage oldest = message(AiMessageRole.ASSISTANT, "respuesta jornada uno");
        when(messages.findRecentByProjectId(eq(projectId), any()))
                .thenReturn(List.of(newest, oldest));

        AiPrompt prompt = builder.build(request(null, null, "pregunta actual", null));

        assertEquals(List.of(AiPromptRole.ASSISTANT, AiPromptRole.USER, AiPromptRole.USER),
                prompt.messages().stream().map(value -> value.role()).toList());
        assertEquals("respuesta jornada uno", prompt.messages().get(0).content());
        assertEquals("mensaje jornada dos", prompt.messages().get(1).content());
    }

    @Test
    void historyQueryIsProjectScopedAndLimited() {
        builder(3, 8000, 24000).build(request(null, null, "actual", null));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        verify(messages).findRecentByProjectId(eq(projectId), pageable.capture());
        assertEquals(3, pageable.getValue().getPageSize());
    }

    @Test
    void persistedCurrentMessageIsExcludedAndNotDuplicated() {
        UUID currentMessageId = UUID.randomUUID();
        when(messages.findRecentByProjectIdExcludingMessage(
                eq(projectId), eq(currentMessageId), any())).thenReturn(List.of());

        AiPrompt prompt = builder.build(request(null, null, "única pregunta", currentMessageId));

        assertEquals(1, prompt.messages().size());
        assertEquals("única pregunta", prompt.messages().getFirst().content());
        verify(messages).findRecentByProjectIdExcludingMessage(
                eq(projectId), eq(currentMessageId), any());
    }

    @Test
    void contextDoesNotExposeHostEmailOrAuthenticationSecrets() {
        AiPrompt prompt = builder.build(request(null, null, "Pregunta", null));

        assertTrue(prompt.systemInstruction().contains("Host: Colaborador"));
        assertFalse(prompt.systemInstruction().contains("member@example.com"));
        assertFalse(prompt.systemInstruction().toLowerCase().contains("jwt"));
        assertFalse(prompt.systemInstruction().toLowerCase().contains("api key"));
    }

    @Test
    void newestMessagesWinWhenTotalBudgetIsSmall() {
        AiMessage newest = message(AiMessageRole.ASSISTANT, "NUEVO");
        AiMessage older = message(AiMessageRole.USER, "ANTIGUO-" + "x".repeat(500));
        when(messages.findRecentByProjectId(eq(projectId), any()))
                .thenReturn(List.of(newest, older));

        AiPrompt prompt = builder(30, 8000, 1250).build(
                request(null, null, "actual", null));

        assertTrue(prompt.messages().stream().anyMatch(value -> value.content().equals("NUEVO")));
        assertEquals("NUEVO", prompt.messages().get(prompt.messages().size() - 2).content());
    }

    @Test
    void excessiveDataIsTruncatedDeterministicallyAndCurrentQuestionIsPreserved() {
        project = project(projectId, "Ventas", "x".repeat(5000));
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        AiContextBuilder small = builder(30, 100, 1300);

        AiPrompt first = small.build(request(null, null, "actual", null));
        AiPrompt second = small.build(request(null, null, "actual", null));

        assertEquals(first.systemInstruction(), second.systemInstruction());
        assertTrue(first.systemInstruction().contains("Contexto truncado"));
        assertEquals("actual", first.messages().getLast().content());
    }

    @Test
    void rejectsOversizedCurrentMessage() {
        assertCode(AiContextErrorCode.MESSAGE_INVALID,
                () -> builder(30, 5, 24000).build(request(null, null, "demasiado", null)));
    }

    private AiContextBuilder builder(int historyLimit, int maxMessage, int maxContext) {
        AiProviderProperties properties = new AiProviderProperties(
                AiProviderProperties.Provider.OLLAMA, maxMessage, historyLimit, maxContext,
                new AiProviderProperties.Ollama("http://localhost", "model", Duration.ofSeconds(1)),
                new AiProviderProperties.Gemini("", "model", "http://localhost", Duration.ofSeconds(1)));
        return new AiContextBuilder(projects, sessions, models, diagrams, messages, umlSummary, properties);
    }

    private AiContextRequest request(UUID modelId, UUID diagramId, String content, UUID messageId) {
        return new AiContextRequest(projectId, sessionId, modelId, diagramId, content, messageId);
    }

    private AiContextRequest request(UUID modelId, UUID diagramId, String content, UUID messageId,
            AiSkill skill) {
        return new AiContextRequest(projectId, sessionId, modelId, diagramId, content, messageId, skill);
    }

    private Project project(UUID id, String name, String description) {
        Project value = mock(Project.class);
        when(value.getId()).thenReturn(id);
        when(value.getName()).thenReturn(name);
        when(value.getDescription()).thenReturn(description);
        when(value.getVisibility()).thenReturn(ProjectVisibility.PRIVATE);
        return value;
    }

    private AiSession session(UUID id, Project owner, boolean active) {
        AiSession value = mock(AiSession.class);
        User host = mock(User.class);
        when(host.getDisplayName()).thenReturn("Colaborador");
        when(host.getEmail()).thenReturn("member@example.com");
        when(value.getId()).thenReturn(id);
        when(value.getProject()).thenReturn(owner);
        when(value.getHost()).thenReturn(host);
        when(value.getStartedAt()).thenReturn(Instant.parse("2026-10-25T12:00:00Z"));
        when(value.getStatus()).thenReturn(active ? AiSessionStatus.ACTIVE : AiSessionStatus.CLOSED);
        when(value.isActive()).thenReturn(active);
        return value;
    }

    private UmlModel model(UUID id, Project owner) {
        UmlModel value = mock(UmlModel.class);
        when(value.getId()).thenReturn(id);
        when(value.getProject()).thenReturn(owner);
        when(value.getName()).thenReturn("Dominio");
        when(value.getDocumentation()).thenReturn("Modelo principal");
        when(value.getUmlVersion()).thenReturn("2.5.1");
        when(value.getRevision()).thenReturn(4L);
        when(value.getStatus()).thenReturn(UmlModelStatus.EDITABLE);
        return value;
    }

    private Diagram diagram(UUID id, UmlModel model, String name) {
        Diagram value = mock(Diagram.class);
        when(value.getId()).thenReturn(id);
        when(value.getModel()).thenReturn(model);
        when(value.getName()).thenReturn(name);
        when(value.getType()).thenReturn(DiagramType.CLASS);
        return value;
    }

    private AiMessage message(AiMessageRole role, String content) {
        AiMessage value = mock(AiMessage.class);
        when(value.getRole()).thenReturn(role);
        when(value.getContent()).thenReturn(content);
        return value;
    }

    private void assertCode(AiContextErrorCode code, Runnable invocation) {
        assertEquals(code, assertThrows(AiContextException.class, invocation::run).getCode());
    }
}
