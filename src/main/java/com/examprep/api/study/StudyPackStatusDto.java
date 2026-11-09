package com.examprep.api.study;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe generation status. {@code failureReason} is a server generated message and never
 * contains document text or provider secrets.
 */
public record StudyPackStatusDto(UUID studyPackId,
                                 StudyPackStatus status,
                                 String progressMessage,
                                 String failureReason,
                                 Instant createdAt,
                                 Instant updatedAt) {
}
