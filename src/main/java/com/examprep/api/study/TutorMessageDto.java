package com.examprep.api.study;

import com.examprep.api.document.SourceReferenceDto;
import java.time.Instant;
import java.util.List;

public record TutorMessageDto(String role,
                              String content,
                              List<SourceReferenceDto> sourceReferences,
                              Instant createdAt) {
}
