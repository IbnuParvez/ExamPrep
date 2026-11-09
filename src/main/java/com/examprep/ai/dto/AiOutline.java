package com.examprep.ai.dto;

import java.util.List;

public record AiOutline(String title,
                        String introduction,
                        String summary,
                        List<String> learningObjectives,
                        List<String> revisionPriorities,
                        List<AiTopic> topics) {
}
