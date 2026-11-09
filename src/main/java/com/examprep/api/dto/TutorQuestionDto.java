package com.examprep.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A question for the AI study tutor. */
public record TutorQuestionDto(
        @NotBlank(message = "Type a question for the tutor.")
        @Size(max = 2000, message = "Keep your question under 2000 characters.")
        String message
) {
}
