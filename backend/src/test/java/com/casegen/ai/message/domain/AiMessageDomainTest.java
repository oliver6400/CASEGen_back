package com.casegen.ai.message.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.ai.session.domain.AiSession;
import com.casegen.auth.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AiMessageDomainTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void userFactoryPreservesItsInvariants() {
        AiSession session = org.mockito.Mockito.mock(AiSession.class);
        User author = org.mockito.Mockito.mock(User.class);
        UUID clientMessageId = UUID.randomUUID();

        AiMessage message = AiMessage.userMessage(
                session, author, "Validate the model", null, null, clientMessageId, CREATED_AT);

        assertEquals(AiMessageRole.USER, message.getRole());
        assertEquals(AiSkill.GENERAL, message.getSkill());
        assertSame(author, message.getAuthor());
        assertNull(message.getProvider());
        assertEquals(clientMessageId, message.getClientMessageId());
        assertEquals(CREATED_AT, message.getCreatedAt());
    }

    @Test
    void assistantFactoryPreservesItsInvariantsAndReply() {
        AiSession session = org.mockito.Mockito.mock(AiSession.class);
        User author = org.mockito.Mockito.mock(User.class);
        AiMessage user = AiMessage.userMessage(
                session, author, "Question", null, null, UUID.randomUUID(), CREATED_AT);

        AiMessage answer = AiMessage.assistantMessage(
                session, "Answer", "ollama", null, null, user, CREATED_AT.plusSeconds(1));

        assertEquals(AiMessageRole.ASSISTANT, answer.getRole());
        assertEquals(AiSkill.GENERAL, answer.getSkill());
        assertNull(answer.getAuthor());
        assertEquals("ollama", answer.getProvider());
        assertSame(user, answer.getReplyTo());
        assertEquals(CREATED_AT.plusSeconds(1), answer.getCreatedAt());
    }

    @Test
    void assistantCopiesSkillFromUser() {
        AiSession session = org.mockito.Mockito.mock(AiSession.class);
        User author = org.mockito.Mockito.mock(User.class);
        AiMessage user = AiMessage.userMessage(session, author, "Review", null, null,
                UUID.randomUUID(), AiSkill.UML_REVIEWER, CREATED_AT);

        AiMessage answer = AiMessage.assistantMessage(session, "Finding", "ollama",
                null, null, user, CREATED_AT.plusSeconds(1));

        assertEquals(AiSkill.UML_REVIEWER, user.getSkill());
        assertEquals(AiSkill.UML_REVIEWER, answer.getSkill());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void factoriesRejectBlankContent(String content) {
        AiSession session = org.mockito.Mockito.mock(AiSession.class);
        User author = org.mockito.Mockito.mock(User.class);

        assertThrows(IllegalArgumentException.class, () -> AiMessage.userMessage(
                session, author, content, null, null, UUID.randomUUID(), CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> AiMessage.assistantMessage(
                session, content, "ollama", null, null, null, CREATED_AT));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void assistantFactoryRejectsBlankProvider(String provider) {
        AiSession session = org.mockito.Mockito.mock(AiSession.class);

        assertThrows(IllegalArgumentException.class, () -> AiMessage.assistantMessage(
                session, "Answer", provider, null, null, null, CREATED_AT));
    }
}
