package com.examprep.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw tutor response before reference validation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TutorModelResponse(String answer, Boolean answeredFromMaterial, List<String> sources) {
}
