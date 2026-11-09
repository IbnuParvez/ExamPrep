package com.examprep.document.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Supported academic document formats, with the content types Tika may report for each.
 */
public enum DocumentFormat {

    PDF("pdf", "PDF", Set.of("application/pdf")),
    DOCX("docx", "Word document",
            Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml", "application/zip", "application/octet-stream")),
    DOC("doc", "Word document",
            Set.of("application/msword", "application/x-tika-msoffice", "application/x-ole-storage")),
    PPTX("pptx", "PowerPoint presentation",
            Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/x-tika-ooxml", "application/zip", "application/octet-stream")),
    PPT("ppt", "PowerPoint presentation",
            Set.of("application/vnd.ms-powerpoint", "application/x-tika-msoffice", "application/x-ole-storage")),
    TXT("txt", "Text file", Set.of("text/plain", "application/octet-stream")),
    MARKDOWN("md", "Markdown file", Set.of("text/markdown", "text/x-web-markdown", "text/plain")),
    RTF("rtf", "Rich text document", Set.of("application/rtf", "text/rtf")),
    ODT("odt", "OpenDocument text",
            Set.of("application/vnd.oasis.opendocument.text", "application/zip")),
    ODP("odp", "OpenDocument presentation",
            Set.of("application/vnd.oasis.opendocument.presentation", "application/zip"));

    private static final Set<String> REJECTED_FAMILIES = Set.of(
            "text/html", "application/xhtml+xml", "application/x-msdownload",
            "application/x-executable", "application/x-sharedlib");

    private final String extension;
    private final String label;
    private final Set<String> acceptedContentTypes;

    DocumentFormat(String extension, String label, Set<String> acceptedContentTypes) {
        this.extension = extension;
        this.label = label;
        this.acceptedContentTypes = acceptedContentTypes;
    }

    public String extension() {
        return extension;
    }

    public String label() {
        return label;
    }

    public static Set<String> supportedExtensions() {
        return Arrays.stream(values())
                .map(DocumentFormat::extension)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static Optional<DocumentFormat> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        for (DocumentFormat format : values()) {
            if (format.extension.equals(normalized)) {
                return Optional.of(format);
            }
        }
        if ("markdown".equals(normalized)) {
            return Optional.of(MARKDOWN);
        }
        return Optional.empty();
    }

    public static Optional<DocumentFormat> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        return fromExtension(filename.substring(dot + 1));
    }

    /**
     * Accepts a Tika detected content type when it belongs to this format's family. Files whose
     * real content belongs to a different family (images, executables, HTML) are rejected even if
     * the extension claims otherwise.
     */
    public boolean acceptsDetectedContentType(String detectedContentType) {
        if (detectedContentType == null || detectedContentType.isBlank()) {
            return false;
        }
        String type = detectedContentType.trim().toLowerCase(Locale.ROOT);
        if (REJECTED_FAMILIES.contains(type) || type.startsWith("image/")
                || type.startsWith("audio/") || type.startsWith("video/")) {
            return false;
        }
        if (acceptedContentTypes.contains(type)) {
            return true;
        }
        return (this == TXT || this == MARKDOWN) && type.startsWith("text/");
    }
}
