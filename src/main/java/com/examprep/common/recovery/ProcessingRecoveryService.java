package com.examprep.common.recovery;

import com.examprep.api.document.DocumentProcessingStatus;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.study.service.StudyPackStateWriter;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The processing queues live in memory, so any job that is still marked in progress when the
 * application starts was interrupted by a restart. Those records are failed with a clear message
 * instead of remaining stuck forever.
 */
@Component
public class ProcessingRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingRecoveryService.class);

    private final DocumentRepository documentRepository;
    private final StudyPackRepository studyPackRepository;

    public ProcessingRecoveryService(DocumentRepository documentRepository,
                                     StudyPackRepository studyPackRepository) {
        this.documentRepository = documentRepository;
        this.studyPackRepository = studyPackRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedJobs() {
        Instant now = Instant.now();
        int documents = documentRepository.markInterrupted(
                List.of(DocumentProcessingStatus.UPLOADED, DocumentProcessingStatus.PROCESSING),
                DocumentProcessingStatus.FAILED,
                "Processing was interrupted when the application restarted. Please upload the file again.",
                now);
        int packs = studyPackRepository.markInterrupted(
                List.of(StudyPackStatus.QUEUED, StudyPackStatus.PROCESSING),
                StudyPackStatus.FAILED,
                StudyPackStateWriter.INTERRUPTED_REASON,
                now);
        if (documents > 0 || packs > 0) {
            log.warn("Recovered {} interrupted documents and {} interrupted study packages",
                    documents, packs);
        }
    }
}
