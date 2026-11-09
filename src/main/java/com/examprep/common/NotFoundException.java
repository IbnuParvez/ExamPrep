package com.examprep.common;

/** The requested record does not exist for this student (also used to hide foreign records). */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(message);
    }
}
