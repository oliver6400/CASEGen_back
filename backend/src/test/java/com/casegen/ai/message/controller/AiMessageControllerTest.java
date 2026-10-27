package com.casegen.ai.message.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casegen.ai.message.domain.AiMessage;
import com.casegen.ai.message.dto.AiMessageMapper;
import com.casegen.ai.message.exception.AiMessageErrorCode;
import com.casegen.ai.message.exception.AiMessageException;
import com.casegen.ai.message.service.AiMessageService;
import com.casegen.ai.message.service.SendAiMessageResult;
import com.casegen.ai.provider.AiProviderErrorCode;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiMessageController.class)
@Import({AiMessageMapper.class, AiMessageExceptionHandler.class,
        AiMessageControllerTest.TestSecurityConfiguration.class})
@TestPropertySource(properties = {
        "casegen.security.jwt.issuer=casegen",
        "casegen.security.jwt.secret=01234567890123456789012345678901",
        "casegen.security.jwt.access-token-ttl=PT15M",
        "casegen.security.jwt.refresh-token-ttl=P30D",
        "casegen.security.cors.allowed-origins=http://localhost:5173",
        "casegen.security.cors.allowed-origin-patterns="
})
class AiMessageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiMessageService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private AiMessage userMessage;
    private AiMessage assistantMessage;

    @BeforeEach
    void setUp() {
        User author = new User("private@test.local", "secret-hash", "Visible name");
        ReflectionTestUtils.setField(author, "id", UUID.randomUUID());
        Project project = new Project("Project", null);
        ReflectionTestUtils.setField(project, "id", projectId);
        AiSession session = new AiSession(project, author, Instant.parse("2026-10-26T10:00:00Z"));
        ReflectionTestUtils.setField(session, "id", sessionId);
        userMessage = AiMessage.userMessage(session, author, "question", null, null,
                clientId, Instant.parse("2026-10-26T10:01:00Z"));
        ReflectionTestUtils.setField(userMessage, "id", UUID.randomUUID());
        assistantMessage = AiMessage.assistantMessage(session, "answer", "OLLAMA", null, null,
                userMessage, Instant.parse("2026-10-26T10:01:01Z"));
        ReflectionTestUtils.setField(assistantMessage, "id", UUID.randomUUID());
    }

    @Test
    void requestWithoutJwtIsUnauthorized() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }

    @Test
    void projectOutsiderIsForbidden() throws Exception {
        when(service.history(projectId, sessionId, 0, 50))
                .thenThrow(new AccessDeniedException("denied"));
        mvc.perform(get(path()).with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void newMessageReturnsCreatedAndSafeResponse() throws Exception {
        when(service.send(any(), any(), any()))
                .thenReturn(new SendAiMessageResult(userMessage, assistantMessage, false));

        mvc.perform(post(path()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(clientId, "question")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.userMessage.skill").value("GENERAL"))
                .andExpect(jsonPath("$.userMessage.authorName").value("Visible name"))
                .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.assistantMessage.skill").value("GENERAL"))
                .andExpect(jsonPath("$.assistantMessage.provider").value("OLLAMA"))
                .andExpect(jsonPath("$.assistantMessage.replyToMessageId")
                        .value(userMessage.getId().toString()))
                .andExpect(jsonPath("$.userMessage.email").doesNotExist())
                .andExpect(jsonPath("$.systemInstruction").doesNotExist())
                .andExpect(jsonPath("$.thinking").doesNotExist());
    }

    @Test
    void replayReturnsOk() throws Exception {
        when(service.send(any(), any(), any()))
                .thenReturn(new SendAiMessageResult(userMessage, assistantMessage, true));
        mvc.perform(post(path()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(clientId, "question")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    void historyReturnsRecentPageInChronologicalContentOrder() throws Exception {
        PageRequest page = PageRequest.of(0, 50);
        when(service.history(projectId, sessionId, 0, 50))
                .thenReturn(new PageImpl<>(List.of(assistantMessage, userMessage), page, 2));
        mvc.perform(get(path()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].skill").value("GENERAL"))
                .andExpect(jsonPath("$.content[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void invalidSkillIsBadRequest() throws Exception {
        mvc.perform(post(path()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientId
                                + "\",\"content\":\"question\",\"skill\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPaginationIsBadRequest() throws Exception {
        when(service.history(projectId, sessionId, -1, 50))
                .thenThrow(new AiMessageException(AiMessageErrorCode.MESSAGE_INVALID, "invalid page"));
        mvc.perform(get(path() + "?page=-1&size=50").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MESSAGE_INVALID"));
    }

    @ParameterizedTest
    @MethodSource("providerErrors")
    void providerErrorsAreControlled(AiProviderErrorCode code, int expected) throws Exception {
        when(service.send(any(), any(), any()))
                .thenThrow(new AiProviderException(code, "OLLAMA", "sensitive internal detail"));
        mvc.perform(post(path()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(clientId, "question")))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        "sensitive internal detail")));
    }

    @ParameterizedTest
    @MethodSource("messageErrors")
    void messageErrorsAreMapped(AiMessageErrorCode code, int expected) throws Exception {
        when(service.send(any(), any(), any()))
                .thenThrow(new AiMessageException(code, "controlled"));
        mvc.perform(post(path()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(clientId, "question")))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(code.name()));
    }

    private static Stream<Arguments> providerErrors() {
        return Stream.of(
                Arguments.of(AiProviderErrorCode.NOT_CONFIGURED, 503),
                Arguments.of(AiProviderErrorCode.UNAVAILABLE, 503),
                Arguments.of(AiProviderErrorCode.TIMEOUT, 504),
                Arguments.of(AiProviderErrorCode.RATE_LIMITED, 429),
                Arguments.of(AiProviderErrorCode.INVALID_RESPONSE, 502));
    }

    private static Stream<Arguments> messageErrors() {
        return Stream.of(
                Arguments.of(AiMessageErrorCode.SESSION_NOT_FOUND, 404),
                Arguments.of(AiMessageErrorCode.SESSION_NOT_ACTIVE, 409),
                Arguments.of(AiMessageErrorCode.CLIENT_MESSAGE_ID_REUSED, 409));
    }

    private String path() {
        return "/api/projects/" + projectId + "/ai/sessions/" + sessionId + "/messages";
    }

    private String body(UUID id, String content) {
        return "{\"clientMessageId\":\"" + id + "\",\"content\":\"" + content + "\"}";
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
