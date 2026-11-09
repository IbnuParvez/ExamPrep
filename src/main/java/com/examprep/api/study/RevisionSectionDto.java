package com.examprep.api.study;

import com.examprep.api.document.SourceReferenceDto;
import java.util.List;

/**
 * A section of detailed revision notes.
 */
public record RevisionSectionDto(String heading,
                                 String narrative,
                                 List<String> keyPoints,
                                 List<SourceReferenceDto> sourceReferences) {
}
