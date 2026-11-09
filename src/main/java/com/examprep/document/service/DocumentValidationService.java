package com.examprep.document.service;

import com.examprep.common.ValidationException;
import com.examprep.config.AppProperties;
import com.examprep.document.domain.DocumentFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploads without trusting browser supplied metadata: the extension must be on the
 * allow list, the size must fit the configured limit and the detected content type must belong
 * to the same document family.
 */
@Service
public class DocumentValidationService {

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final int MAX_FILENAME_LENGTH = 255;

    private final AppProperties properties;
    private final Tika tika = new Tika();

    public DocumentValidationService(AppProperties properties) {
        this.properties = properties;
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Please choose a file to upload.");
        }
        String originalFilename = sanitiseFilename(file.getOriginalFilename());
        if (originalFilename.isBlank()) {
            throw new ValidationException("The uploaded file does not have a usable name.");
        }
        DocumentFormat format = DocumentFormat.fromFilename(originalFilename)
                .orElseThrow(() -> new ValidationException(
                        "That file type is not supported. Upload a PDF, DOCX, DOC, PPTX, PPT, TXT, Markdown, RTF, ODT or ODP file."));

        long size = file.getSize();
        long maxBytes = properties.upload().maxSizeBytes();
        if (size <= 0) {
            throw new ValidationException("The uploaded file is empty.");
        }
        if (size > maxBytes) {
            throw new ValidationException("That file is " + megabytes(size) + ". The maximum upload size is "
                    + properties.upload().maxSizeMb() + " MB.");
        }

        String detectedContentType;
        try (InputStream input = file.getInputStream()) {
            detectedContentType = tika.detect(input, originalFilename);
        } catch (IOException failure) {
            throw new ValidationException("The uploaded file could not be read.");
        }
        if (!format.acceptsDetectedContentType(detectedContentType)) {
            throw new ValidationException("The file content does not look like a supported "
                    + format.label() + " file. Upload a genuine ." + format.extension() + " document.");
        }
        return new ValidatedUpload(originalFilename, format, detectedContentType, size);
    }

    static String sanitiseFilename(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = CONTROL_CHARACTERS.matcher(name).replaceAll("").trim();
        if (name.length() > MAX_FILENAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 ? name.substring(dot) : "";
            name = name.substring(0, Math.max(1, MAX_FILENAME_LENGTH - extension.length())) + extension;
        }
        return name;
    }

    private static String megabytes(long bytes) {
        double value = bytes / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", value);
    }
}
