package com.examprep.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.examprep.common.ValidationException;
import com.examprep.config.StorageProperties;
import com.examprep.document.service.DocumentValidator;
import com.examprep.document.service.ValidatedUpload;

class DocumentValidatorTest {

    private final DocumentValidator validator = createValidator(25);

    @Test
    void acceptsPlainTextAndReportsDetectedType() {
        MockMultipartFile file = new MockMultipartFile("file", "lecture-notes.txt", "text/plain",
                "Demand and supply are core economic concepts.".getBytes(StandardCharsets.UTF_8));

        ValidatedUpload upload = validator.validate(file);

        assertThat(upload.extension()).isEqualTo("txt");
        assertThat(upload.contentType()).startsWith("text/plain");
        assertThat(upload.originalFilename()).isEqualTo("lecture-notes.txt");
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "application/octet-stream",
                new byte[]{0x4d, 0x5a, 0x00, 0x01});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchItsExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "pretend.pdf", "application/pdf",
                "This is plainly not a PDF file.".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not match its extension");
    }

    @Test
    void rejectsEmptyFiles() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsOversizedFiles() {
        DocumentValidator smallLimit = createValidator(1);
        byte[] bytes = new byte[1024 * 1024 + 10];
        java.util.Arrays.fill(bytes, (byte) 'a');
        MockMultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", bytes);

        assertThatThrownBy(() -> smallLimit.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void stripsDirectoryComponentsFromClientFilenames() {
        assertThat(DocumentValidator.sanitizeFilename("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(DocumentValidator.sanitizeFilename("C:\\notes\\lecture.txt")).isEqualTo("lecture.txt");
    }

    private DocumentValidator createValidator(int maxMb) {
        StorageProperties properties = new StorageProperties();
        properties.setMaxFileSizeMb(maxMb);
        return new DocumentValidator(properties);
    }
}
