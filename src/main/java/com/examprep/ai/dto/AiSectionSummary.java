package com.examprep.ai.dto;

import java.util.List;

public record AiSectionSummary(String heading,
                               String summary,
                               List<String> keyPoints,
                               List<AiDefinition> definitions,
                               List<AiFormula> formulas,
                               List<String> sourceRefs) {
}
