package com.casegen.generation.backend.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class GenerationExecutorConfig {
    @Bean(name = "generationExecutor")
    public Executor generationExecutor(
            @Value("${casegen.generation.executor.core-size:1}") int coreSize,
            @Value("${casegen.generation.executor.max-size:2}") int maxSize,
            @Value("${casegen.generation.executor.queue-capacity:20}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("casegen-generation-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
