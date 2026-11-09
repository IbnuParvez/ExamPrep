package com.examprep.api.dto;

import java.util.UUID;

import com.examprep.api.model.StudyPackStatus;

/** Returned when a generation job has been accepted. */
public record StudyPackCreationResult(UUID studyPackId, StudyPackStatus status) {
}
