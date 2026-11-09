package com.examprep.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.examprep.api.DocumentApplicationService;
import com.examprep.api.UserRegistrationService;
import com.examprep.api.dto.DocumentContext;
import com.examprep.api.dto.DocumentStatusDto;
import com.examprep.api.dto.DocumentUploadResult;
import com.examprep.api.dto.RegisterUserRequest;
import com.examprep.api.dto.UserAccountDto;
import com.examprep.api.model.DocumentStatus;
import com.examprep.common.NotFoundException;
import com.examprep.common.ValidationException;
import com.examprep.support.AbstractIntegrationTest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

class DocumentWorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final String STUDY_TEXT = """
            Demand and supply are the foundations of price formation in a market economy.

            When demand increases and supply is fixed, the equilibrium price rises. When supply increases and
            demand is unchanged, the equilibrium price falls. Elasticity measures how strongly quantity responds
            to a change in price, income or the price of a related good.

            Goods with close substitutes usually have more elastic demand. Producers with spare capacity can
            respond to higher prices, so their supply is more elastic in the short run.
            """;

    @Autowired
    private DocumentApplicationService documentService;

    @Autowired
    private UserRegistrationService registrationService;

    @Test
    void uploadedTextIsExtractedIntoStableChunks() {
        UserAccountDto owner = register("Grace");
        DocumentUploadResult upload = upload(owner, "lecture-notes.txt", STUDY_TEXT);

        await(Duration.ofSeconds(20), () ->
                documentService.getStatus(upload.documentId(), owner.id()).status() == DocumentStatus.READY);

        DocumentStatusDto status = documentService.getStatus(upload.documentId(), owner.id());
        assertThat(status.chunkCount()).isPositive();
        assertThat(status.originalFilename()).isEqualTo("lecture-notes.txt");

        DocumentContext context = documentService.getContext(upload.documentId(), owner.id());
        assertThat(context.chunks()).isNotEmpty();
        assertThat(context.chunks().get(0).refId()).isEqualTo("c1");
        assertThat(context.chunks().get(0).locationLabel()).isEqualTo("Document text");
        assertThat(context.totalChars()).isGreaterThan(200);
    }

    @Test
    void anotherStudentCannotReadOrDeleteTheDocument() throws Exception {
        UserAccountDto owner = register("Owner");
        UserAccountDto stranger = register("Stranger");
        DocumentUploadResult upload = upload(owner, "private-notes.txt", STUDY_TEXT);
        await(Duration.ofSeconds(20), () ->
                documentService.getStatus(upload.documentId(), owner.id()).status() == DocumentStatus.READY);

        assertThatThrownBy(() -> documentService.getStatus(upload.documentId(), stranger.id()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> documentService.getContext(upload.documentId(), stranger.id()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> documentService.delete(upload.documentId(), stranger.id()))
                .isInstanceOf(NotFoundException.class);

        mockMvc.perform(get("/documents/" + upload.documentId()).with(user(securityUser(stranger))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/documents/" + upload.documentId() + "/status")
                        .with(user(securityUser(stranger)))
                        .header("Accept", "application/json"))
                .andExpect(status().isNotFound());
    }

    @Test
    void documentPagesRenderForTheOwner() throws Exception {
        UserAccountDto owner = register("Owner Pages");
        DocumentUploadResult upload = upload(owner, "rendered-notes.txt", STUDY_TEXT);
        await(Duration.ofSeconds(20), () ->
                documentService.getStatus(upload.documentId(), owner.id()).status() == DocumentStatus.READY);

        mockMvc.perform(get("/documents").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rendered-notes.txt")));

        mockMvc.perform(get("/documents/" + upload.documentId()).with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Extraction status")))
                .andExpect(content().string(containsString("ready")));

        mockMvc.perform(get("/documents/upload").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Drop a file here")));

        mockMvc.perform(get("/documents/" + upload.documentId() + "/study").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("How should we prepare this material")));
    }

    @Test
    void invalidAndEmptyDocumentsAreRejectedOrExplained() throws Exception {
        UserAccountDto owner = register("Validation Owner");

        MockMultipartFile executable = new MockMultipartFile("file", "malware.exe",
                "application/octet-stream", new byte[]{0x4d, 0x5a, 0x00});
        assertThatThrownBy(() -> documentService.upload(executable, owner.id()))
                .isInstanceOf(ValidationException.class);

        mockMvc.perform(multipart("/documents").file(executable).with(user(securityUser(owner))).with(csrf()))
                .andExpect(status().isBadRequest());

        DocumentUploadResult empty = upload(owner, "images-only.txt", "   \n\n   \n");
        await(Duration.ofSeconds(20), () ->
                documentService.getStatus(empty.documentId(), owner.id()).status() == DocumentStatus.FAILED);
        DocumentStatusDto status = documentService.getStatus(empty.documentId(), owner.id());
        assertThat(status.errorMessage()).contains("No readable text");
    }

    @Test
    void deletingADocumentRemovesIt() {
        UserAccountDto owner = register("Deleting Owner");
        DocumentUploadResult upload = upload(owner, "temporary.txt", STUDY_TEXT);
        await(Duration.ofSeconds(20), () ->
                documentService.getStatus(upload.documentId(), owner.id()).status() == DocumentStatus.READY);

        documentService.delete(upload.documentId(), owner.id());

        assertThatThrownBy(() -> documentService.getStatus(upload.documentId(), owner.id()))
                .isInstanceOf(NotFoundException.class);
    }

    private UserAccountDto register(String name) {
        String email = name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID() + "@example.com";
        return registrationService.register(new RegisterUserRequest(name, email, "study-password-1"));
    }

    private DocumentUploadResult upload(UserAccountDto owner, String filename, String content) {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        return documentService.upload(file, owner.id());
    }
}
