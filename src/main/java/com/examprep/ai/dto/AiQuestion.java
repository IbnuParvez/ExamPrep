package com.examprep.ai.dto;

import java.util.List;

public record AiQuestion(String type, String prompt, List<String> options, String correctAnswer,
                         String explanation, String difficulty, String sourceRef) {
}
