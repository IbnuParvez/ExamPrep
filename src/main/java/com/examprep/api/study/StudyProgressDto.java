package com.examprep.api.study;

import java.util.List;

/**
 * Simple progress overview for the dashboard.
 */
public record StudyProgressDto(int documentsReady,
                               int studyPackCount,
                               int completedExams,
                               int flashcardsReviewed,
                               int averageScorePercent,
                               List<StudyPackSummaryDto> recentStudyPacks,
                               List<ExamSummaryDto> recentExams) {
}
