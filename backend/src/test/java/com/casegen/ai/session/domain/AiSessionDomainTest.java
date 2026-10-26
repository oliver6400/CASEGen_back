package com.casegen.ai.session.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiSessionDomainTest {

    @Test
    void newSessionStartsActiveWithoutEndDate() {
        AiSession session = session(Instant.parse("2026-10-25T12:00:00Z"));

        assertTrue(session.isActive());
        assertEquals(AiSessionStatus.ACTIVE, session.getStatus());
        assertNull(session.getEndedAt());
    }

    @Test
    void closeChangesStatusAndRecordsEndDateOnlyOnce() {
        AiSession session = session(Instant.parse("2026-10-25T12:00:00Z"));
        Instant endedAt = Instant.parse("2026-10-25T13:00:00Z");

        session.close(endedAt);
        session.close(endedAt.plusSeconds(60));

        assertFalse(session.isActive());
        assertEquals(AiSessionStatus.CLOSED, session.getStatus());
        assertEquals(endedAt, session.getEndedAt());
    }

    @Test
    void participantTouchPreservesJoinDateAndUpdatesLastSeen() {
        Instant joinedAt = Instant.parse("2026-10-25T12:05:00Z");
        Instant lastSeenAt = joinedAt.plusSeconds(300);
        AiSession session = mock(AiSession.class);
        User user = mock(User.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(user.getId()).thenReturn(UUID.randomUUID());
        AiSessionParticipant participant = new AiSessionParticipant(session, user, joinedAt);

        participant.touch(lastSeenAt);

        assertEquals(joinedAt, participant.getJoinedAt());
        assertEquals(lastSeenAt, participant.getLastSeenAt());
    }

    @Test
    void participantIdUsesBothIdentifiersForEquality() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiSessionParticipantId first = new AiSessionParticipantId(sessionId, userId);
        AiSessionParticipantId same = new AiSessionParticipantId(sessionId, userId);
        AiSessionParticipantId different = new AiSessionParticipantId(sessionId, UUID.randomUUID());

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertFalse(first.equals(different));
    }

    private AiSession session(Instant startedAt) {
        return new AiSession(new Project("AI project", null), user("host"), startedAt);
    }

    private User user(String name) {
        return new User(name + "@test.local", "hash", name);
    }
}
