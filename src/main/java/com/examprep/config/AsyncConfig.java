package com.examprep.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executors for document extraction and AI generation. The browser never waits on these
 * tasks directly; it polls the persisted processing status instead.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String DOCUMENT_EXECUTOR = "documentTaskExecutor";
    public static final String STUDY_EXECUTOR = "studyTaskExecutor";

    private final ProcessingProperties properties;
    private final AiProperties aiProperties;

    public AsyncConfig(ProcessingProperties properties, AiProperties aiProperties) {
        this.properties = properties;
        this.aiProperties = aiProperties;
    }

    @Bean(name = DOCUMENT_EXECUTOR)
    public Executor documentTaskExecutor() {
        return buildExecutor("document-", properties.getDocumentThreads());
    }

    @Bean(name = STUDY_EXECUTOR)
    public Executor studyTaskExecutor() {
        return buildExecutor("study-", properties.getStudyThreads());
    }

    private Executor buildExecutor(String prefix, int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // Must outlast a single AI request (examprep.ai.timeout-seconds, otherwise a redeploy
        // interrupts every generation in flight).
        executor.setAwaitTerminationSeconds(Math.max(60, aiProperties.getTimeoutSeconds() + 60));
        executor.initialize();
        return executor;
    }
}
