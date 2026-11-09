package com.examprep.api.study;

import java.util.UUID;

public record FlashcardDto(UUID id, String front, String back, String sourceReference) {
}
