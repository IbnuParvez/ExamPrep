package com.examprep.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Tutor response. {@code sources} are validated document locations. {@code answeredFromMaterial}
 * is false when the uploaded material did not contain enough information to answer.
 */
public record TutorAnswerDto(String answer, List<String> sources, boolean answeredFromMaterial, Instant answeredAt) {
}
