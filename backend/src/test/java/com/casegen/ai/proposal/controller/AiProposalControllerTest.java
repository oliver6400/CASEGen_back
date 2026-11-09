package com.casegen.ai.proposal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.dto.AiProposalMapper;
import com.casegen.ai.proposal.dto.AiProposalResponse;
import com.casegen.ai.proposal.dto.PagedAiProposalResponse;
import com.casegen.ai.proposal.service.AiProposalErrorCode;
import com.casegen.ai.proposal.service.AiProposalException;
import com.casegen.ai.proposal.service.AiProposalDecisionService;
import com.casegen.ai.proposal.service.AiProposalBatchApplyAllService;
import com.casegen.ai.proposal.service.AiProposalBatchApplyAllResult;
import com.casegen.ai.proposal.service.AiProposalBatchQueryService;
import com.casegen.ai.proposal.review.AiProposalBatchCreationService;
import com.casegen.ai.proposal.review.CreatedAiProposalBatch;
import com.casegen.ai.proposal.review.AiReviewGenerationException;
import com.casegen.ai.proposal.review.AiReviewValidationException;
import com.casegen.ai.proposal.review.AiReviewValidationErrorCode;
import com.casegen.ai.proposal.domain.AiProposalBatch;
import com.casegen.ai.proposal.dto.AiProposalBatchResponse;
import com.casegen.ai.proposal.dto.PagedAiProposalBatchResponse;
import com.casegen.ai.proposal.service.AiProposalService;
import com.casegen.ai.proposal.service.CreateAiProposalResult;
import com.casegen.ai.proposal.service.AiProposalGenerationErrorCode;
import com.casegen.ai.proposal.service.AiProposalGenerationException;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.auth.domain.User;
import com.casegen.auth.service.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AiProposalController.class)
@Import({AiProposalExceptionHandler.class, AiProposalControllerTest.TestSecurityConfiguration.class})
@TestPropertySource(properties = {
        "casegen.security.jwt.issuer=casegen",
        "casegen.security.jwt.secret=01234567890123456789012345678901",
        "casegen.security.jwt.access-token-ttl=PT15M",
        "casegen.security.jwt.refresh-token-ttl=P30D",
        "casegen.security.cors.allowed-origins=http://localhost:5173",
        "casegen.security.cors.allowed-origin-patterns="
})
class AiProposalControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiProposalService service;
    @MockitoBean AiProposalDecisionService decisions;
    @MockitoBean AiProposalMapper mapper;
    @MockitoBean CurrentUserService currentUsers;
    @MockitoBean AiProposalBatchApplyAllService applyAll;
    @MockitoBean AiProposalBatchCreationService batchCreation;
    @MockitoBean AiProposalBatchQueryService batchQuery;
    private final UUID projectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private AiProposal proposal;
    private AiProposalResponse response;
    private User currentUser;

    @BeforeEach
    void setUp() {
        proposal = org.mockito.Mockito.mock(AiProposal.class);
        currentUser = org.mockito.Mockito.mock(User.class);
        when(currentUser.getId()).thenReturn(UUID.randomUUID());
        when(currentUsers.requireCurrentUser()).thenReturn(currentUser);
        response = response();
        when(mapper.toResponse(proposal)).thenReturn(response);
    }

    @Test
    void createsFullReviewBatchAndReturns201() throws Exception {
        UUID batchId=UUID.randomUUID();
        AiProposalBatch batch=org.mockito.Mockito.mock(AiProposalBatch.class);
        AiProposalBatchResponse batchResponse=org.mockito.Mockito.mock(AiProposalBatchResponse.class);
        when(batch.getId()).thenReturn(batchId);
        when(batchResponse.id()).thenReturn(batchId);
        when(batchCreation.create(any())).thenReturn(new CreatedAiProposalBatch(batch,false));
        when(batchQuery.detail(projectId,batchId)).thenReturn(batchResponse);

        mvc.perform(post("/api/projects/{projectId}/ai/sessions/{sessionId}/messages/{messageId}/proposal-batches",
                projectId,sessionId,messageId).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientBatchId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location","/api/projects/"+projectId+"/ai/proposal-batches/"+batchId));
    }

    @Test
    void listsAndReadsProposalBatches() throws Exception {
        UUID batchId=UUID.randomUUID();
        when(batchQuery.list(projectId,0,20)).thenReturn(new PagedAiProposalBatchResponse(
                List.of(),0,20,0,0,true,true));
        AiProposalBatchResponse response=org.mockito.Mockito.mock(AiProposalBatchResponse.class);
        when(response.id()).thenReturn(batchId);
        when(batchQuery.detail(projectId,batchId)).thenReturn(response);

        mvc.perform(get("/api/projects/{projectId}/ai/proposal-batches",projectId).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/projects/{projectId}/ai/proposal-batches/{batchId}",projectId,batchId).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(batchId.toString()));
    }

    @Test
    void invalidProviderReviewIsControlled502InsteadOf500() throws Exception {
        when(batchCreation.create(any())).thenThrow(
                new AiReviewGenerationException("review must contain between 1 and 15 items"));
        mvc.perform(post("/api/projects/{projectId}/ai/sessions/{sessionId}/messages/{messageId}/proposal-batches",
                projectId,sessionId,messageId).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientBatchId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVALID_REVIEW_RESPONSE"));
    }

    @Test
    void incompatibleReviewIsControlled422InsteadOf500() throws Exception {
        when(batchCreation.create(any())).thenThrow(new AiReviewValidationException(
                AiReviewValidationErrorCode.INVALID_PAYLOAD,"invalid review payload"));
        mvc.perform(post("/api/projects/{projectId}/ai/sessions/{sessionId}/messages/{messageId}/proposal-batches",
                projectId,sessionId,messageId).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientBatchId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    void reusedClientBatchIdIsControlled409InsteadOf500() throws Exception {
        when(batchCreation.create(any())).thenThrow(new AiProposalException(
                AiProposalErrorCode.CLIENT_BATCH_ID_REUSED,
                "clientBatchId is already associated with another request"));

        mvc.perform(post("/api/projects/{projectId}/ai/sessions/{sessionId}/messages/{messageId}/proposal-batches",
                        projectId, sessionId, messageId).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientBatchId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_BATCH_ID_REUSED"));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(listPath())).andExpect(status().isUnauthorized());
    }

    @Test
    void externalUserIsForbidden() throws Exception {
        when(service.list(projectId, null, null, null, null, null, 0, 30))
                .thenThrow(new AccessDeniedException("denied"));
        mvc.perform(get(listPath()).with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void newProposalReturns201LocationAndSafeBody() throws Exception {
        when(service.create(any(), any(), any(), any()))
                .thenReturn(new CreateAiProposalResult(proposal, false));
        mvc.perform(post(createPath()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientProposalId\":\"" + clientId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", listPath() + "/" + proposalId))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.proposal.provider").value("GEMINI"))
                .andExpect(jsonPath("$.proposal.requestedByName").value("Member"))
                .andExpect(jsonPath("$.proposal.requestedByEmail").doesNotExist());
    }

    @Test
    void replayReturns200() throws Exception {
        when(service.create(any(), any(), any(), any()))
                .thenReturn(new CreateAiProposalResult(proposal, true));
        mvc.perform(post(createPath()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientProposalId\":\"" + clientId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    void clientCannotSupplyServerControlledFields() throws Exception {
        mvc.perform(post(createPath()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientProposalId\":\"" + clientId
                                + "\",\"provider\":\"CLIENT_CHOICE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAndDetailReturn200() throws Exception {
        PagedAiProposalResponse page = new PagedAiProposalResponse(List.of(response), 0, 30, 1, 1,
                true, true);
        when(service.list(projectId, null, null, null, null, null, 0, 30)).thenReturn(Page.empty());
        when(mapper.toPaged(any())).thenReturn(page);
        when(service.detail(projectId, proposalId)).thenReturn(proposal);
        mvc.perform(get(listPath()).with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(proposalId.toString()));
        mvc.perform(get(listPath() + "/" + proposalId).with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(proposalId.toString()));
    }

    @Test
    void applyAndRejectUseAuthenticatedActorAndReturnUpdatedProposal() throws Exception {
        when(decisions.apply(any())).thenReturn(proposal);
        when(decisions.reject(any())).thenReturn(proposal);

        mvc.perform(post(decisionPath("apply")).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"approved\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(proposalId.toString()));
        mvc.perform(post(decisionPath("reject")).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(proposalId.toString()));

        verify(currentUsers, org.mockito.Mockito.times(2)).requireCurrentUser();
    }

    @Test
    void anonymousCannotApplyOrReject() throws Exception {
        mvc.perform(post(decisionPath("apply")).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(decisionPath("reject")).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void decisionNoteOverLimitIsBadRequest() throws Exception {
        String note = "x".repeat(1001);
        mvc.perform(post(decisionPath("apply")).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + note + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decisionAccessDeniedIsForbidden() throws Exception {
        when(decisions.apply(any())).thenThrow(new AccessDeniedException("denied"));
        mvc.perform(post(decisionPath("apply")).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("decisionErrors")
    void decisionErrorsUseDedicatedStatus(AiProposalErrorCode code, int expected) throws Exception {
        when(decisions.apply(any())).thenThrow(new AiProposalException(code, "internal detail"));
        mvc.perform(post(decisionPath("apply")).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code.name()));
    }

    @ParameterizedTest
    @MethodSource("proposalErrors")
    void proposalErrorsAreMapped(AiProposalErrorCode code, int expected) throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(new AiProposalException(code, "safe"));
        mvc.perform(post(createPath()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientProposalId\":\"" + clientId + "\"}"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code.name()));
    }

    @ParameterizedTest
    @MethodSource("providerErrors")
    void providerErrorsAreSafe(AiProviderErrorCode code, int expected) throws Exception {
        when(service.create(any(), any(), any(), any()))
                .thenThrow(new AiProviderException(code, "GEMINI", "secret detail"));
        mvc.perform(post(createPath()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientProposalId\":\"" + clientId + "\"}"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code.name()));
    }

    @Test
    void noApplicableIs422() throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(new AiProposalGenerationException(
                AiProposalGenerationErrorCode.NO_APPLICABLE_UML_PROPOSAL, "not applicable"));
        mvc.perform(post(createPath()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientProposalId\":\"" + clientId + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void applyAllUsesAuthenticatedActorAndReturnsSummary() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(applyAll.apply(any())).thenReturn(
                new AiProposalBatchApplyAllResult(batchId, 6, 17L,
                        Instant.parse("2026-10-31T12:00:00Z")));

        mvc.perform(post("/api/projects/" + projectId
                        + "/ai/proposal-batches/" + batchId + "/apply").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.appliedCount").value(6))
                .andExpect(jsonPath("$.finalModelRevision").value(17));
        verify(applyAll).apply(any());
    }

    private AiProposalResponse response() {
        return new AiProposalResponse(proposalId, null, projectId, sessionId, messageId,
                UUID.randomUUID(), "Member", AiProposalStatus.PENDING,
                AiProposalType.ADD_CLASSIFIER, "Add Order", "Needed",
                JsonMapper.builder().build().valueToTree(java.util.Map.of("name", "Order")),
                1, UUID.randomUUID(), "Model", null, null, 1L, null, 1L, null,
                false, false, null, null, null, null, null, null,
                "GEMINI", AiSkill.UML_REVIEWER, Instant.now(), Instant.now());
    }

    private String createPath() {
        return "/api/projects/" + projectId + "/ai/sessions/" + sessionId
                + "/messages/" + messageId + "/proposals";
    }

    private String listPath() {
        return "/api/projects/" + projectId + "/ai/proposals";
    }

    private String decisionPath(String decision) {
        return listPath() + "/" + proposalId + "/" + decision;
    }

    static Stream<Arguments> proposalErrors() {
        return Stream.of(Arguments.of(AiProposalErrorCode.CLIENT_PROPOSAL_ID_REUSED, 409),
                Arguments.of(AiProposalErrorCode.CONTEXT_REVISION_CHANGED, 409),
                Arguments.of(AiProposalErrorCode.PROPOSAL_NOT_FOUND, 404),
                Arguments.of(AiProposalErrorCode.INVALID_REQUEST, 400));
    }

    static Stream<Arguments> providerErrors() {
        return Stream.of(Arguments.of(AiProviderErrorCode.RATE_LIMITED, 429),
                Arguments.of(AiProviderErrorCode.INVALID_RESPONSE, 502),
                Arguments.of(AiProviderErrorCode.UNAVAILABLE, 503),
                Arguments.of(AiProviderErrorCode.TIMEOUT, 504));
    }

    static Stream<Arguments> decisionErrors() {
        return Stream.of(Arguments.of(AiProposalErrorCode.PROPOSAL_NOT_FOUND, 404),
                Arguments.of(AiProposalErrorCode.PROPOSAL_ALREADY_DECIDED, 409),
                Arguments.of(AiProposalErrorCode.PROPOSAL_STALE, 409),
                Arguments.of(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID, 422));
    }

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            (request, response, exception) ->
                                    response.sendError(HttpStatus.UNAUTHORIZED.value())))
                    .build();
        }
    }
}
