package com.examprep.api.dto;

/** Dashboard counters for one student, computed over every submitted attempt. */
public record StudyStatsDto(int attemptCount, int bestScore, int averageScore) {
}
