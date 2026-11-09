package com.examprep.api.dto;

import java.util.UUID;

import com.examprep.api.model.StudyPackStatus;

/** Polled by the browser while a study package is generated. */
public record StudyPackStatusDto(
        UUID id,
        StudyPackStatus status,
        String progressMessage,
        String errorMessage,
        int progressPercent
) {
    public boolean ready() {
        return status == StudyPackStatus.READY;
    }

    public boolean failed() {
        return status == StudyPackStatus.FAILED;
    }

    public boolean inProgress() {
        return status == StudyPackStatus.QUEUED || status == StudyPackStatus.PROCESSING;
    }
}
