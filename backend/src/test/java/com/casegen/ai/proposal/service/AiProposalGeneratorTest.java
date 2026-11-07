package com.casegen.ai.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.message.repository.AiMessageRepository;
import com.casegen.ai.proposal.context.AiProposalContext;
import com.casegen.ai.proposal.context.AiProposalContextBuilder;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.payload.AddGeneralizationPayload;
import com.casegen.ai.proposal.payload.AiProposalPayloadParser;
import com.casegen.ai.proposal.validation.AiProposalPayloadValidator;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import com.casegen.ai.provider.AiPrompt;
import com.casegen.ai.provider.AiProviderResult;
import com.casegen.ai.provider.SelectedAiProvider;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.project.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class AiProposalGeneratorTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AiMessageRepository messages = mock(AiMessageRepository.class);
    private final AiProposalContextBuilder contexts = mock(AiProposalContextBuilder.class);
    private final SelectedAiProvider provider = mock(SelectedAiProvider.class);
    private final AiProposalPromptBuilder prompts = new AiProposalPromptBuilder();
    private final AiProposalGenerator generator = new AiProposalGenerator(messages, contexts,
            prompts, provider, new AiProposalPayloadParser(json),
            new AiProposalPayloadValidator(), json);
    private UUID projectId;
    private UUID sessionId;
    private UUID sourceId;
    private UUID modelId;
    private UUID specificId;
    private UUID generalId;
    private AiMessage source;
    private AiProposalContext context;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        modelId = UUID.randomUUID();
        specificId = UUID.randomUUID();
        generalId = UUID.randomUUID();
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        AiSession session = mock(AiSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getProject()).thenReturn(project);
        UmlModel model = mock(UmlModel.class);
        when(model.getId()).thenReturn(modelId);
        source = mock(AiMessage.class);
        when(source.getId()).thenReturn(sourceId);
        when(source.getRole()).thenReturn(AiMessageRole.ASSISTANT);
        when(source.getSkill()).thenReturn(AiSkill.UML_REVIEWER);
        when(source.getProvider()).thenReturn("OLLAMA");
        when(source.getContent()).thenReturn("Cliente debe heredar de Usuario");
        when(source.getSession()).thenReturn(session);
        when(source.getModel()).thenReturn(model);
        when(messages.findDetailedById(sourceId)).thenReturn(Optional.of(source));
        context = new AiProposalContext(projectId, modelId, 9, null, null, List.of(),
                List.of(new AiProposalContext.ClassifierEntry(specificId, "Cliente",
                                ClassifierKind.CLASS, null, true),
                        new AiProposalContext.ClassifierEntry(generalId, "Usuario",
                                ClassifierKind.CLASS, null, true)),
                List.of(), List.of(), List.of());
        when(contexts.build(projectId, modelId, null)).thenReturn(context);
    }

    @Test
    void generatesOneTypedValidatedCanonicalProposalAndUsesActualProvider() {
        when(provider.generate(any())).thenReturn(result(validJson(), "GEMINI"));

        GeneratedAiProposal generated = generator.generate(command());

        assertThat(generated.proposalType()).isEqualTo(AiProposalType.ADD_GENERALIZATION);
        assertThat(generated.payload()).isInstanceOf(AddGeneralizationPayload.class);
        assertThat(generated.canonicalPayload().path("specificClassifierId").asText())
                .isEqualTo(specificId.toString());
        assertThat(generated.baseModelRevision()).isEqualTo(9);
        assertThat(generated.provider()).isEqualTo("GEMINI");
        assertThat(generated.skill()).isEqualTo(AiSkill.UML_REVIEWER);
        assertThat(generated.sourceMessageId()).isEqualTo(sourceId);
    }

    @Test
    void promptUsesSourceSuggestionAndCompactCatalogWithoutGeometry() {
        when(provider.generate(any())).thenReturn(result(validJson(), "GEMINI"));
        generator.generate(command());
        ArgumentCaptor<AiPrompt> captor = ArgumentCaptor.forClass(AiPrompt.class);
        verify(provider).generate(captor.capture());
        String prompt = captor.getValue().messages().getFirst().content();
        assertThat(prompt).contains("Cliente debe heredar de Usuario", specificId.toString(),
                generalId.toString(), "visible=true");
        assertThat(prompt).doesNotContain("waypoints", "sourceAnchorIndex", "geometry");
        assertThat(captor.getValue().systemInstruction()).contains("exactamente UN objeto JSON",
                "No inventes UUIDs", "DATA no confiable", "ADD_GENERALIZATION:",
                "specificClassifierId", "UUID exacto del descendiente",
                "PUBLIC, PRIVATE, PROTECTED o PACKAGE", "typeKind=PRIMITIVE",
                "STRING, INTEGER, BOOLEAN, REAL o UNLIMITED_NATURAL",
                "NONE, SHARED o COMPOSITE", "ASSOCIATION o CLASSIFIER",
                "Nunca devuelvas un array", "elige exactamente UNA");
    }

    @Test
    void acceptsOnlyCompleteJsonFenceAsMinimalSanitization() {
        when(provider.generate(any())).thenReturn(result("```json\n" + validJson() + "\n```", "OLLAMA"));
        assertThat(generator.generate(command()).provider()).isEqualTo("OLLAMA");
    }

    @Test
    void noApplicableSentinelIsMappedWithoutTryingToParsePayload() {
        when(provider.generate(any())).thenReturn(result("NO_APPLICABLE_UML_PROPOSAL", "GEMINI"));
        assertCode(() -> generator.generate(command()),
                AiProposalGenerationErrorCode.NO_APPLICABLE_UML_PROPOSAL);
    }

    @Test
    void rejectsNarrativeMarkdownUnknownRootAndMultipleObjects() {
        rejectsProviderResponse("Here is JSON: " + validJson());
        rejectsProviderResponse(jsonWithExtraRootField());
        rejectsProviderResponse(validJson() + validJson());
        rejectsProviderResponse(validJson().replace("\"title\":\"Agregar herencia\",", ""));
        rejectsProviderResponse("[]");
    }

    @Test
    void rejectsInvalidReferencedIdAfterProviderCall() {
        String response = validJson().replace(generalId.toString(), UUID.randomUUID().toString());
        when(provider.generate(any())).thenReturn(result(response, "GEMINI"));
        assertThatThrownBy(() -> generator.generate(command()))
                .isInstanceOf(AiProposalValidationException.class);
    }

    @Test
    void rejectsMissingSourceBeforeProviderCall() {
        when(messages.findDetailedById(sourceId)).thenReturn(Optional.empty());
        assertCode(() -> generator.generate(command()),
                AiProposalGenerationErrorCode.SOURCE_MESSAGE_NOT_FOUND);
        verify(provider, never()).generate(any());
    }

    @Test
    void rejectsUserGeneralDatabaseReviewerWrongSessionAndMissingModel() {
        when(source.getRole()).thenReturn(AiMessageRole.USER);
        rejectsInvalidSource();
        when(source.getRole()).thenReturn(AiMessageRole.ASSISTANT);
        when(source.getSkill()).thenReturn(AiSkill.GENERAL);
        rejectsInvalidSource();
        when(source.getSkill()).thenReturn(AiSkill.DATABASE_REVIEWER);
        rejectsInvalidSource();
        when(source.getSkill()).thenReturn(AiSkill.UML_REVIEWER);
        assertCode(() -> generator.generate(new GenerateAiProposalCommand(projectId,
                UUID.randomUUID(), sourceId)), AiProposalGenerationErrorCode.INVALID_SOURCE_MESSAGE);
        when(source.getModel()).thenReturn(null);
        rejectsInvalidSource();
        verify(provider, never()).generate(any());
    }

    private void rejectsInvalidSource() {
        assertCode(() -> generator.generate(command()),
                AiProposalGenerationErrorCode.INVALID_SOURCE_MESSAGE);
    }

    private void rejectsProviderResponse(String content) {
        when(provider.generate(any())).thenReturn(result(content, "GEMINI"));
        assertCode(() -> generator.generate(command()),
                AiProposalGenerationErrorCode.INVALID_PROVIDER_RESPONSE);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            AiProposalGenerationErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(AiProposalGenerationException.class)
                .extracting(error -> ((AiProposalGenerationException) error).getCode())
                .isEqualTo(code);
    }

    private GenerateAiProposalCommand command() {
        return new GenerateAiProposalCommand(projectId, sessionId, sourceId);
    }

    private AiProviderResult result(String content, String providerName) {
        return new AiProviderResult(content, providerName, "test-model", "STOP");
    }

    private String validJson() {
        return """
                {"proposalType":"ADD_GENERALIZATION","title":"Agregar herencia",
                 "rationale":"Evita duplicacion","payload":{"specificClassifierId":"%s",
                 "generalClassifierId":"%s","substitutable":true,"documentation":null}}
                """.formatted(specificId, generalId);
    }

    private String jsonWithExtraRootField() {
        return validJson().replaceFirst("\\{", "{\"status\":\"PENDING\",");
    }
}
