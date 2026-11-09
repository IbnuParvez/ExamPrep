package com.examprep.ai.dto;

import java.util.List;

public record AiRevisionSection(String heading, String narrative, List<String> keyPoints,
                                List<String> sourceRefs) {
}
