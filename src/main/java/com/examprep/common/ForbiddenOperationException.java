package com.examprep.common;

/** The authenticated student is not allowed to perform this operation. */
public class ForbiddenOperationException extends AppException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
