package com.examprep.study.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.examprep.api.model.StudyPackStatus;
import com.examprep.config.ProcessingProperties;
import com.examprep.study.repository.StudyPackRepository;

/**
 * A generation job cannot survive an application restart, so stale in-progress packages are marked
 * failed with clear guidance instead of spinning forever.
 */
@Component
public class StudyStaleJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StudyStaleJobRecovery.class);
    private static final String REASON = "Generation was interrupted before it finished. "
            + "Delete this package and generate it again.";

    private final StudyPackRepository repository;
    private final ProcessingProperties properties;

    public StudyStaleJobRecovery(StudyPackRepository repository, ProcessingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    public void recover() {
        Instant cutoff = Instant.now().minus(properties.getStaleAfterMinutes(), ChronoUnit.MINUTES);
        int recovered = repository.markStaleJobsFailed(StudyPackStatus.FAILED,
                List.of(StudyPackStatus.QUEUED, StudyPackStatus.PROCESSING), cutoff, REASON, Instant.now());
        if (recovered > 0) {
            log.warn("Recovered {} stale study generation job(s)", recovered);
        }
    }
}
