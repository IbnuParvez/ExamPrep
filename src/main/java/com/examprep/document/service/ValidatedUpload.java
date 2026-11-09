package com.examprep.document.service;

/**
 * A file that passed extension, size and real-content validation.
 *
 * @param originalFilename sanitised original name, used only as display metadata
 * @param extension        lower-case extension without the dot
 * @param contentType      content type detected from the bytes, never trusted from the browser
 * @param sizeBytes        actual uploaded size
 */
public record ValidatedUpload(String originalFilename, String extension, String contentType, long sizeBytes) {
}
