package com.examprep.api.dto;

import java.time.Instant;
import java.util.List;

/** One persisted tutor conversation message. */
public record TutorMessageDto(String role, String content, List<String> sources, Instant createdAt) {
}
