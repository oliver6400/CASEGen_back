package com.casegen.auth.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void validRefreshIsUsableOnlyBeforeExpirationForAnEnabledUser() {
        User user = mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        Instant now = Instant.parse("2026-10-30T12:00:00Z");

        assertTrue(new RefreshToken(user, "hash", now.plusSeconds(60)).isUsableAt(now));
        assertFalse(new RefreshToken(user, "hash", now).isUsableAt(now));
        assertFalse(new RefreshToken(user, "hash", now.minusSeconds(1)).isUsableAt(now));
    }

    @Test
    void disabledUserCannotUseAnOtherwiseValidRefreshToken() {
        User user = mock(User.class);
        when(user.isEnabled()).thenReturn(false);
        Instant now = Instant.parse("2026-10-30T12:00:00Z");

        assertFalse(new RefreshToken(user, "hash", now.plusSeconds(60)).isUsableAt(now));
    }

    @Test
    void revokedRefreshTokenIsRejected() {
        User user = mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        Instant now = Instant.parse("2026-10-30T12:00:00Z");
        RefreshToken token = new RefreshToken(user, "hash", now.plusSeconds(60));
        token.revoke(now);

        assertFalse(token.isUsableAt(now));
    }
}
