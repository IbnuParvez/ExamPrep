package com.examprep.document.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.examprep.api.model.DocumentStatus;
import com.examprep.config.ProcessingProperties;
import com.examprep.document.repository.DocumentRepository;

/**
 * In-progress documents cannot survive an application restart. Rather than leaving students with a
 * permanently spinning status, stale jobs are marked failed with an actionable message and can be
 * re-queued with Retry extraction.
 */
@Component
public class DocumentStaleJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentStaleJobRecovery.class);
    private static final String REASON = "Processing was interrupted before it finished. "
            + "Use Retry extraction to try again.";

    private final DocumentRepository documentRepository;
    private final ProcessingProperties properties;

    public DocumentStaleJobRecovery(DocumentRepository documentRepository, ProcessingProperties properties) {
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Extraction runs on an in-memory executor, so a restart kills every in-progress document.
        // Recovering them immediately avoids status pages that spin until the stale window elapses.
        recoverAll();
    }

    private void recoverAll() {
        // A cutoff in the near future matches every in-progress row, so all of them are recovered now.
        Instant cutoff = Instant.now().plusSeconds(1);
        int recovered = documentRepository.markStaleJobsFailed(DocumentStatus.FAILED,
                List.of(DocumentStatus.UPLOADED, DocumentStatus.PROCESSING), cutoff, REASON, Instant.now());
        if (recovered > 0) {
            log.warn("Recovered {} interrupted document job(s) on startup", recovered);
        }
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    public void recover() {
        Instant cutoff = Instant.now().minus(properties.getStaleAfterMinutes(), ChronoUnit.MINUTES);
        int recovered = documentRepository.markStaleJobsFailed(DocumentStatus.FAILED,
                List.of(DocumentStatus.UPLOADED, DocumentStatus.PROCESSING), cutoff, REASON, Instant.now());
        if (recovered > 0) {
            log.warn("Recovered {} stale document job(s)", recovered);
        }
    }
}
