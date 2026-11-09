package com.examprep.ai.service;

import com.examprep.api.study.Difficulty;
import com.examprep.api.study.QuestionType;
import java.util.List;

public record QuestionDraft(QuestionType type,
                            String prompt,
                            List<String> options,
                            String correctAnswer,
                            String explanation,
                            Difficulty difficulty,
                            String sourceReference,
                            boolean examEligible) {
}
