package com.examprep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Background processing pool and stale-job recovery settings. */
@ConfigurationProperties(prefix = "examprep.processing")
public class ProcessingProperties {

    private int documentThreads = 2;
    private int studyThreads = 2;
    private int queueCapacity = 100;
    private int staleAfterMinutes = 60;

    public int getDocumentThreads() {
        return documentThreads;
    }

    public void setDocumentThreads(int documentThreads) {
        this.documentThreads = documentThreads;
    }

    public int getStudyThreads() {
        return studyThreads;
    }

    public void setStudyThreads(int studyThreads) {
        this.studyThreads = studyThreads;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getStaleAfterMinutes() {
        return staleAfterMinutes;
    }

    public void setStaleAfterMinutes(int staleAfterMinutes) {
        this.staleAfterMinutes = staleAfterMinutes;
    }
}
