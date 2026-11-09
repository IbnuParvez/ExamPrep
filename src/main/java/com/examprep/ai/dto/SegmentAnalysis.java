package com.examprep.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Intermediate analysis of one section of a large document. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SegmentAnalysis(String summary, List<String> keyPoints, List<String> topics) {
}
