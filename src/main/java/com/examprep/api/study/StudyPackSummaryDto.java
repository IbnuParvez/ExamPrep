package com.examprep.api.study;

import java.time.Instant;
import java.util.UUID;

public record StudyPackSummaryDto(UUID studyPackId,
                                  UUID documentId,
                                  String title,
                                  String documentFilename,
                                  StudyPackStatus status,
                                  int flashcardCount,
                                  int questionCount,
                                  Integer latestScore,
                                  Integer latestTotal,
                                  Instant createdAt) {
}
