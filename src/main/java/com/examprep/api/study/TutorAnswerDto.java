package com.examprep.api.study;

import com.examprep.api.document.SourceReferenceDto;
import java.time.Instant;
import java.util.List;

/**
 * Tutor reply. {@code insufficientInformation} is true when the uploaded material does not
 * answer the question, so the interface can say so plainly instead of inventing content.
 */
public record TutorAnswerDto(String answer,
                             List<SourceReferenceDto> sourceReferences,
                             boolean insufficientInformation,
                             Instant createdAt) {
}
