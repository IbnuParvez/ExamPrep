package com.examprep.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.examprep.common.ProcessingFailedException;
import com.examprep.common.ValidationException;
import com.examprep.config.StorageProperties;

/**
 * Validates uploads by extension, size and detected content. The browser-supplied filename and
 * MIME type are treated as untrusted hints only.
 */
@Component
public class DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidator.class);

    /** Extension to acceptable detected media types. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "pdf", Set.of("application/pdf"),
            // Tika reports some OOXML containers generically; without this, valid DOCX/PPTX files were
            // rejected as "content does not match its extension".
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml"),
            "doc", Set.of("application/msword", "application/x-tika-msoffice"),
            "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/x-tika-ooxml"),
            "ppt", Set.of("application/vnd.ms-powerpoint", "application/x-tika-msoffice"),
            "txt", Set.of("text/plain"),
            "md", Set.of("text/plain", "text/markdown", "text/x-web-markdown"),
            "rtf", Set.of("application/rtf", "text/rtf", "text/plain"),
            "odt", Set.of("application/vnd.oasis.opendocument.text"),
            "odp", Set.of("application/vnd.oasis.opendocument.presentation")
    );

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "rtf");

    private final StorageProperties properties;
    private final Tika tika = new Tika();

    public DocumentValidator(StorageProperties properties) {
        this.properties = properties;
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null) {
            throw new ValidationException("Choose a file to upload.");
        }
        if (file.getSize() == 0) {
            throw new ValidationException("The uploaded file is empty.");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ValidationException("The uploaded file has no filename.");
        }
        String cleanedName = sanitizeFilename(originalFilename);
        String extension = extensionOf(cleanedName);
        Set<String> acceptedTypes = ALLOWED.get(extension);
        if (acceptedTypes == null) {
            throw new ValidationException("Unsupported file type. Upload a PDF, DOCX, DOC, PPTX, PPT, TXT, "
                    + "Markdown, RTF, ODT or ODP file.");
        }
        long maxBytes = properties.maxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new ValidationException("This file is too large. The maximum size is "
                    + properties.getMaxFileSizeMb() + " MB.");
        }
        String detected = detect(file, cleanedName);
        boolean accepted = acceptedTypes.contains(detected)
                || (TEXT_EXTENSIONS.contains(extension) && detected.startsWith("text/"));
        if (!accepted) {
            log.info("Rejected upload with extension {} because detected type was {}", extension, detected);
            throw new ValidationException("The file content does not match its extension. Upload the original "
                    + "document without renaming it.");
        }
        return new ValidatedUpload(cleanedName, extension, detected, file.getSize());
    }

    private String detect(MultipartFile file, String filename) {
        try (InputStream inputStream = file.getInputStream()) {
            // The filename hint lets Tika recognise Markdown, LaTeX or shell notes that start with JSON,
            // XML or a \documentclass and would otherwise be detected as a non-text type.
            String detected = tika.detect(inputStream, filename);
            if (detected == null || detected.isBlank()) {
                throw new ValidationException("The file type could not be determined.");
            }
            return detected.toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            throw new ProcessingFailedException("The uploaded file could not be read.", exception);
        }
    }

    /** Keeps a readable name, strips directories and control characters. */
    static String sanitizeFilename(String filename) {
        String cleaned = filename.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isBlank()) {
            throw new ValidationException("The uploaded file has no usable filename.");
        }
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static Set<String> supportedExtensions() {
        return ALLOWED.keySet();
    }
}
