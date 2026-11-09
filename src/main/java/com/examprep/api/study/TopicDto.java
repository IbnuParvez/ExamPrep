package com.examprep.api.study;

import com.examprep.api.document.SourceReferenceDto;
import java.util.List;

public record TopicDto(String title,
                       String explanation,
                       List<String> keyConcepts,
                       List<SourceReferenceDto> sourceReferences) {
}
