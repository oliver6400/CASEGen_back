package com.casegen.auth.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtExpirationTest {

    @Test
    void expiredAccessTokenIsRejectedByTheApplicationDecoder() {
        SecurityConfig config = new SecurityConfig();
        JwtProperties properties = new JwtProperties(
                "casegen",
                "01234567890123456789012345678901",
                Duration.ofHours(1),
                Duration.ofDays(30));
        var key = config.jwtSecretKey(properties);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("casegen")
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .build();
        String expired = config.jwtEncoder(key).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThrows(JwtValidationException.class, () -> config.jwtDecoder(key, properties).decode(expired));
    }
}
