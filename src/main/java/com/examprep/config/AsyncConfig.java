package com.examprep.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executors for the two long running workflows: document extraction and
 * AI study package generation. Requests never wait for them, so browser threads are
 * not blocked by multi minute jobs.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "documentTaskExecutor")
    public ThreadPoolTaskExecutor documentTaskExecutor() {
        return buildExecutor("doc-", 2, 4, 40);
    }

    @Bean(name = "aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        return buildExecutor("ai-", 2, 3, 20);
    }

    private ThreadPoolTaskExecutor buildExecutor(String prefix, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
