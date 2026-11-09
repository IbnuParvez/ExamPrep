package com.examprep.api.study;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TutorQuestionDto(
        @NotBlank(message = "Please enter a question.")
        @Size(max = 2000, message = "Please keep tutor questions under 2000 characters.")
        String question) {
}
