package com.casegen.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsPropertiesTest {

    @Test
    void allowsFlutterWebOnDynamicLocalhostPort() {
        CorsProperties properties = new CorsProperties(
                List.of("http://localhost:5173"),
                List.of("http://localhost:[*]", "http://127.0.0.1:[*]"));
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("http://localhost:60005"))
                .isEqualTo("http://localhost:60005");
        assertThat(configuration.checkOrigin("http://127.0.0.1:61234"))
                .isEqualTo("http://127.0.0.1:61234");
        assertThat(configuration.checkOrigin("https://untrusted.example"))
                .isNull();
    }
}
