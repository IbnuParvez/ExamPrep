package com.examprep.api.study;

import java.util.UUID;

public record StudyPackCreationResult(UUID studyPackId, StudyPackStatus status) {
}
