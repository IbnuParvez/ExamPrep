package com.examprep.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.examprep.api.document.DocumentProcessingStatus;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.common.recovery.ProcessingRecoveryService;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentFormat;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.security.AppUserPrincipal;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.support.IntegrationTestBase;
import com.examprep.support.StudyFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProcessingRecoveryTest extends IntegrationTestBase {

    @Autowired
    private ProcessingRecoveryService recoveryService;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;

    @Test
    void interruptedWorkIsFailedWithAReadableReason() {
        AppUserPrincipal student = createStudent("recovery-" + UUID.randomUUID() + "@example.com");
        Document pendingDocument = documentRepository.save(Document.uploaded(student.userId(),
                "interrupted.pdf", "application/pdf", DocumentFormat.PDF,
                student.userId() + "/seed/interrupted.pdf", 2048));
        StudyPack queuedPack = studyPackRepository.save(StudyPack.queued(student.userId(),
                pendingDocument.getId(), "Interrupted revision", "English", "Undergraduate", null,
                4, 4, 20));

        recoveryService.failInterruptedJobs();

        Document recoveredDocument = documentRepository.findById(pendingDocument.getId()).orElseThrow();
        assertThat(recoveredDocument.getStatus()).isEqualTo(DocumentProcessingStatus.FAILED);
        assertThat(recoveredDocument.getFailureReason()).containsIgnoringCase("interrupted");

        StudyPack recoveredPack = studyPackRepository.findById(queuedPack.getId()).orElseThrow();
        assertThat(recoveredPack.getStatus()).isEqualTo(StudyPackStatus.FAILED);
        assertThat(recoveredPack.getFailureReason()).containsIgnoringCase("interrupted");
    }

    @Test
    void finishedWorkIsLeftUntouched() {
        AppUserPrincipal student = createStudent("recovery-done-" + UUID.randomUUID() + "@example.com");
        Document readyDocument = StudyFixtures.seedReadyDocument(documentRepository,
                documentChunkRepository, student.userId());
        StudyPack readyPack = StudyFixtures.seedReadyPack(studyPackRepository, student.userId(),
                readyDocument.getId(), "Finished revision");

        recoveryService.failInterruptedJobs();

        assertThat(documentRepository.findById(readyDocument.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentProcessingStatus.READY);
        assertThat(studyPackRepository.findById(readyPack.getId()).orElseThrow().getStatus())
                .isEqualTo(StudyPackStatus.READY);
    }
}
