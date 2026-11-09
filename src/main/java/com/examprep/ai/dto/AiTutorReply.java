package com.examprep.ai.dto;

import java.util.List;

public record AiTutorReply(String answer, List<String> sourceRefs, Boolean insufficientInformation) {
}
