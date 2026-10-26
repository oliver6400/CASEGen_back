package com.casegen.ai.session.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiSessionConfiguration {

    @Bean
    Clock aiSessionClock() {
        return Clock.systemUTC();
    }
}
