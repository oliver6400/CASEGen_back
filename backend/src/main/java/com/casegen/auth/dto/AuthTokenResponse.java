package com.casegen.auth.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    @Override
    public String toString() {
        return "AuthTokenResponse[accessToken=[REDACTED], refreshToken=[REDACTED], tokenType="
                + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
