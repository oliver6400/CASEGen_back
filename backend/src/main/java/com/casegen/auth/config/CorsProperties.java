package com.casegen.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casegen.security.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedOriginPatterns
) {

    public CorsProperties {
        allowedOrigins = normalize(allowedOrigins);
        allowedOriginPatterns = normalize(allowedOriginPatterns);
    }

    private static List<String> normalize(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
