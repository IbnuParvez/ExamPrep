package com.examprep.study.domain;

import java.util.List;

import com.examprep.api.dto.StudyPackDto;

/** JSONB-persisted sections of a generated study package. */
public record StudyPackageContent(
        List<StudyPackDto.Topic> topics,
        List<StudyPackDto.RevisionSection> revisionSections,
        List<StudyPackDto.Definition> definitions,
        List<StudyPackDto.Formula> formulas
) {
    public static StudyPackageContent empty() {
        return new StudyPackageContent(List.of(), List.of(), List.of(), List.of());
    }
}
