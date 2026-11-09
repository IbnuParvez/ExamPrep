package com.examprep.ai.dto;

import java.util.List;

public record AiTopic(String title, String explanation, List<String> keyConcepts, List<String> sourceRefs) {
}
