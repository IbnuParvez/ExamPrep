package com.examprep.api.dto;

import java.util.UUID;

/** Safe, public view of a student account. Never includes the password hash. */
public record UserAccountDto(UUID id, String fullName, String email) {
}
