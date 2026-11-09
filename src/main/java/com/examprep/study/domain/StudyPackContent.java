package com.examprep.study.domain;

import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.FormulaDto;
import com.examprep.api.study.RevisionSectionDto;
import com.examprep.api.study.TopicDto;
import java.util.List;

/**
 * Validated generated content applied to a study pack when generation succeeds.
 */
public record StudyPackContent(String title,
                               String introduction,
                               String summary,
                               List<String> learningObjectives,
                               List<String> revisionPriorities,
                               List<TopicDto> topics,
                               List<RevisionSectionDto> revisionNotes,
                               List<DefinitionDto> definitions,
                               List<FormulaDto> formulas,
                               List<String> keyFacts,
                               int examDurationMinutes) {
}
