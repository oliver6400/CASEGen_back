package com.casegen.ai.session.controller;

import static com.casegen.ai.session.exception.AiSessionErrorCode.AI_SESSION_CONCURRENT_START;
import static com.casegen.ai.session.exception.AiSessionErrorCode.PROJECT_NOT_FOUND;
import static com.casegen.ai.session.exception.AiSessionErrorCode.SESSION_NOT_ACTIVE;
import static com.casegen.ai.session.exception.AiSessionErrorCode.SESSION_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.ai.session.domain.AiSessionParticipant;
import com.casegen.ai.session.dto.AiSessionMapper;
import com.casegen.ai.session.exception.AiSessionErrorCode;
import com.casegen.ai.session.exception.AiSessionException;
import com.casegen.ai.session.repository.AiSessionHistoryRow;
import com.casegen.ai.session.service.AiSessionService;
import com.casegen.ai.session.service.StartOrReuseSessionResult;
import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiSessionController.class)
@Import({AiSessionMapper.class, AiSessionExceptionHandler.class,
        AiSessionControllerTest.TestSecurityConfiguration.class})
@TestPropertySource(properties = {
        "casegen.security.jwt.issuer=casegen",
        "casegen.security.jwt.secret=01234567890123456789012345678901",
        "casegen.security.jwt.access-token-ttl=PT15M",
        "casegen.security.jwt.refresh-token-ttl=P30D",
        "casegen.security.cors.allowed-origins=http://localhost:5173",
        "casegen.security.cors.allowed-origin-patterns="
})
class AiSessionControllerTest {
    private static final Instant STARTED = Instant.parse("2026-10-25T14:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AiSessionService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private AiSession session;

    @BeforeEach
    void setUp() {
        Project project = new Project("AI project", null);
        ReflectionTestUtils.setField(project, "id", projectId);
        User host = user("Host");
        session = new AiSession(project, host, STARTED);
        ReflectionTestUtils.setField(session, "id", sessionId);
        ReflectionTestUtils.setField(session, "createdAt", STARTED);
        ReflectionTestUtils.setField(session, "updatedAt", STARTED);
        User participantUser = user("Participant");
        session.addParticipant(new AiSessionParticipant(session, participantUser, STARTED.plusSeconds(30)));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get(path("/current"))).andExpect(status().isUnauthorized());
    }

    @Test
    void newSessionReturnsCreatedWithLocation() throws Exception {
        when(service.startOrReuse(projectId)).thenReturn(new StartOrReuseSessionResult(session, true));

        mvc.perform(post(path("")).with(jwt()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", path("/" + sessionId)))
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.hostName").value("Host"))
                .andExpect(jsonPath("$.participants[0].displayName").value("Participant"))
                .andExpect(jsonPath("$.host.email").doesNotExist())
                .andExpect(jsonPath("$.participants[0].email").doesNotExist());
    }

    @Test
    void reusedSessionReturnsOk() throws Exception {
        when(service.startOrReuse(projectId)).thenReturn(new StartOrReuseSessionResult(session, false));

        mvc.perform(post(path("")).with(jwt())).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} can start a session")
    @ValueSource(strings = {"OWNER", "COLLABORATOR"})
    void writeMembersCanStart(String role) throws Exception {
        when(service.startOrReuse(projectId)).thenReturn(new StartOrReuseSessionResult(session, true));

        mvc.perform(post(path("")).with(member(role))).andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{0} cannot start a session")
    @ValueSource(strings = {"VIEWER", "EXTERNAL"})
    void readOnlyOrExternalUsersCannotStart(String role) throws Exception {
        when(service.startOrReuse(projectId)).thenThrow(new AccessDeniedException("denied"));

        mvc.perform(post(path("")).with(member(role))).andExpect(status().isForbidden());
    }

    @Test
    void currentReturnsSessionOrNoContent() throws Exception {
        when(service.getCurrent(projectId)).thenReturn(Optional.of(session), Optional.empty());

        mvc.perform(get(path("/current")).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(1));
        mvc.perform(get(path("/current")).with(jwt()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void detailJoinAndEndReturnDetailedSession() throws Exception {
        when(service.getSession(projectId, sessionId)).thenReturn(session);
        when(service.join(projectId, sessionId)).thenReturn(session);
        when(service.end(projectId, sessionId)).thenReturn(session);

        mvc.perform(get(path("/" + sessionId)).with(jwt())).andExpect(status().isOk());
        mvc.perform(post(path("/" + sessionId + "/join")).with(jwt())).andExpect(status().isOk());
        mvc.perform(post(path("/" + sessionId + "/end")).with(jwt())).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} can read current session")
    @ValueSource(strings = {"OWNER", "COLLABORATOR", "VIEWER"})
    void activeMembersCanReadCurrent(String role) throws Exception {
        when(service.getCurrent(projectId)).thenReturn(Optional.of(session));

        mvc.perform(get(path("/current")).with(member(role))).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} can read session detail")
    @ValueSource(strings = {"OWNER", "COLLABORATOR", "VIEWER"})
    void activeMembersCanReadDetail(String role) throws Exception {
        when(service.getSession(projectId, sessionId)).thenReturn(session);

        mvc.perform(get(path("/" + sessionId)).with(member(role))).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} can join session")
    @ValueSource(strings = {"OWNER", "COLLABORATOR", "VIEWER"})
    void activeMembersCanJoin(String role) throws Exception {
        when(service.join(projectId, sessionId)).thenReturn(session);

        mvc.perform(post(path("/" + sessionId + "/join")).with(member(role)))
                .andExpect(status().isOk());
    }

    @Test
    void closedSessionCannotBeJoined() throws Exception {
        when(service.join(projectId, sessionId))
                .thenThrow(new AiSessionException(SESSION_NOT_ACTIVE, "Session is closed"));

        mvc.perform(post(path("/" + sessionId + "/join")).with(member("VIEWER")))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest(name = "{0} can end session")
    @ValueSource(strings = {"OWNER", "COLLABORATOR"})
    void writeMembersCanEnd(String role) throws Exception {
        when(service.end(projectId, sessionId)).thenReturn(session);

        mvc.perform(post(path("/" + sessionId + "/end")).with(member(role)))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotEndSession() throws Exception {
        when(service.end(projectId, sessionId)).thenThrow(new AccessDeniedException("denied"));

        mvc.perform(post(path("/" + sessionId + "/end")).with(member("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void repeatedEndRemainsSuccessful() throws Exception {
        when(service.end(projectId, sessionId)).thenReturn(session, session);

        mvc.perform(post(path("/" + sessionId + "/end")).with(member("OWNER")))
                .andExpect(status().isOk());
        mvc.perform(post(path("/" + sessionId + "/end")).with(member("OWNER")))
                .andExpect(status().isOk());
    }

    @Test
    void historyIsPagedClampedAndContainsOnlySummaries() throws Exception {
        AiSessionHistoryRow row = Mockito.mock(AiSessionHistoryRow.class);
        when(row.getId()).thenReturn(sessionId);
        when(row.getProjectId()).thenReturn(projectId);
        when(row.getHostUserId()).thenReturn(session.getHost().getId());
        when(row.getHostName()).thenReturn("Host");
        when(row.getStatus()).thenReturn(session.getStatus());
        when(row.getStartedAt()).thenReturn(session.getStartedAt());
        when(row.getEndedAt()).thenReturn(session.getEndedAt());
        when(row.getParticipantCount()).thenReturn(3L);
        var requested = PageRequest.of(2, 100);
        when(service.history(projectId, requested))
                .thenReturn(new PageImpl<>(List.of(row), requested, 300));

        mvc.perform(get(path("?page=2&size=500")).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(300))
                .andExpect(jsonPath("$.content[0].participantCount").value(3))
                .andExpect(jsonPath("$.content[0].participants").doesNotExist());
        verify(service).history(projectId, requested);
    }

    @Test
    void historyRejectsNegativePageAndEmptySize() throws Exception {
        mvc.perform(get(path("?page=-1&size=20")).with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(get(path("?page=0&size=0")).with(jwt())).andExpect(status().isBadRequest());
    }

    @Test
    void externalUserCannotReadHistory() throws Exception {
        when(service.history(any(), any())).thenThrow(new AccessDeniedException("denied"));

        mvc.perform(get(path("")).with(member("EXTERNAL"))).andExpect(status().isForbidden());
    }

    @Test
    void sessionFromAnotherProjectIsNotDisclosed() throws Exception {
        when(service.getSession(projectId, sessionId))
                .thenThrow(new AiSessionException(SESSION_NOT_FOUND, "Session not found"));

        mvc.perform(get(path("/" + sessionId)).with(member("OWNER")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @MethodSource("sessionErrors")
    void mapsDomainErrors(AiSessionErrorCode code, int expectedStatus) throws Exception {
        when(service.getSession(projectId, sessionId))
                .thenThrow(new AiSessionException(code, "Controlled session error"));

        mvc.perform(get(path("/" + sessionId)).with(jwt()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message").value("Controlled session error"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private static Stream<Arguments> sessionErrors() {
        return Stream.of(
                Arguments.of(SESSION_NOT_FOUND, 404),
                Arguments.of(PROJECT_NOT_FOUND, 404),
                Arguments.of(SESSION_NOT_ACTIVE, 409),
                Arguments.of(AI_SESSION_CONCURRENT_START, 409));
    }

    private User user(String name) {
        User user = new User(name.toLowerCase() + "@test.local", "hash", name);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private String path(String suffix) {
        return "/api/projects/" + projectId + "/ai/sessions" + suffix;
    }

    private RequestPostProcessor member(String role) {
        return jwt().jwt(token -> token.claim("projectRole", role));
    }

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            (request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
                    .build();
        }
    }
}
