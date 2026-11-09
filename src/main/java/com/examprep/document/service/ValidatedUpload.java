package com.examprep.document.service;

import com.examprep.document.domain.DocumentFormat;

/**
 * An upload that passed extension, size and content checks.
 */
public record ValidatedUpload(String originalFilename,
                              DocumentFormat format,
                              String detectedContentType,
                              long sizeBytes) {
}
