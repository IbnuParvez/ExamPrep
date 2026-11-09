package com.examprep.common;

/** Input failed a business rule (duplicate email, unsupported file, invalid submission...). */
public class ValidationException extends AppException {

    public ValidationException(String message) {
        super(message);
    }
}
