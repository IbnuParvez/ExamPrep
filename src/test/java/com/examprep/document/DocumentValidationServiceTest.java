package com.examprep.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.examprep.common.ValidationException;
import com.examprep.config.AppProperties;
import com.examprep.document.domain.DocumentFormat;
import com.examprep.document.service.DocumentValidationService;
import com.examprep.document.service.ValidatedUpload;
import com.examprep.support.DocumentFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentValidationServiceTest {

    private final DocumentValidationService service = new DocumentValidationService(new AppProperties(
            new AppProperties.Storage("target/test-uploads"),
            new AppProperties.Upload(1),
            new AppProperties.Processing(30, 1_500_000, 2400, 1800, 200),
            new AppProperties.Study(24000, 12000, 15, 20, 30, 40, 60),
            new AppProperties.Tutor(6)));

    @Test
    void acceptsARealPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf",
                DocumentFixtures.pdf("Energy and matter"));

        ValidatedUpload uploaded = service.validate(file);

        assertThat(uploaded.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(uploaded.originalFilename()).isEqualTo("notes.pdf");
        assertThat(uploaded.detectedContentType()).isEqualTo("application/pdf");
    }

    @Test
    void stripsDirectoryComponentsFromBrowserFilenames() {
        MockMultipartFile file = new MockMultipartFile("file", "C:\\Users\\student\\../../etc/passwd.pdf",
                "application/pdf", DocumentFixtures.pdf("Content"));

        ValidatedUpload uploaded = service.validate(file);

        assertThat(uploaded.originalFilename()).isEqualTo("passwd.pdf");
    }

    @Test
    void rejectsUnsupportedExtensions() {
        MockMultipartFile file = new MockMultipartFile("file", "installer.exe", "application/octet-stream",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void rejectsFilesWhoseContentDoesNotMatchTheExtension() {
        MockMultipartFile impostor = new MockMultipartFile("file", "notes.pdf", "application/pdf",
                DocumentFixtures.jpegHeader());

        assertThatThrownBy(() -> service.validate(impostor))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not look like");
    }

    @Test
    void rejectsOversizedFiles() {
        byte[] bytes = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "huge.pdf", "application/pdf", bytes);

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum upload size");
    }

    @Test
    void rejectsEmptyFiles() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("choose a file");
    }
}
