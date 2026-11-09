package com.examprep.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examprep.api.document.DocumentProcessingStatus;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunk;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.security.AppUserPrincipal;
import com.examprep.support.DocumentFixtures;
import com.examprep.support.IntegrationTestBase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

class DocumentWorkflowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Test
    void pdfUploadBecomesReadyWithPageReferences() throws Exception {
        AppUserPrincipal student = createStudent("pdf-" + UUID.randomUUID() + "@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "photosynthesis.pdf", "application/pdf",
                DocumentFixtures.pdf(
                        "Photosynthesis occurs in chloroplasts, where chlorophyll absorbs light energy and converts it into chemical energy stored in glucose.",
                        "The light reactions take place in the thylakoid membrane, where water is split and ATP and NADPH are produced for the next stage.",
                        "The Calvin cycle uses carbon dioxide and the energy from ATP and NADPH to build glucose, releasing oxygen as a by-product."));

        String location = mockMvc.perform(multipart("/documents").file(file).with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        UUID documentId = documentIdFrom(location);
        Document document = awaitReady(documentId);
        assertThat(document.getStatus()).isEqualTo(DocumentProcessingStatus.READY);
        assertThat(document.getPageCount()).isEqualTo(3);

        var chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(DocumentChunk::getLocationLabel)
                .containsExactly("Page 1", "Page 2", "Page 3");
        assertThat(chunks.get(0).getReferenceId()).isEqualTo("S1");

        mockMvc.perform(get("/api/documents/{id}/status", documentId).with(user(student)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("READY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.chunkCount").value(3));
    }

    @Test
    void docxAndPptxUploadsBecomeReady() throws Exception {
        AppUserPrincipal student = createStudent("office-" + UUID.randomUUID() + "@example.com");
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("Enzyme kinetics",
                "Enzymes are biological catalysts that lower the activation energy required for reactions. Their activity depends on temperature, pH and substrate concentration.");
        sections.put("Inhibition",
                "Competitive inhibitors bind to the active site and slow the reaction rate, while non-competitive inhibitors change the shape of the enzyme.");

        MockMultipartFile docx = new MockMultipartFile("file", "enzymes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DocumentFixtures.docx(sections));
        String docxLocation = mockMvc.perform(multipart("/documents").file(docx).with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(awaitReady(documentIdFrom(docxLocation)).getStatus()).isEqualTo(DocumentProcessingStatus.READY);

        MockMultipartFile pptx = new MockMultipartFile("file", "slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                DocumentFixtures.pptx(
                        "Enzymes are proteins that catalyse metabolic reactions by lowering activation energy.",
                        "The active site is specific to its substrate, which explains why enzymes are highly selective catalysts.",
                        "Temperature, pH and substrate concentration all affect the rate at which an enzyme works."));
        String pptxLocation = mockMvc.perform(multipart("/documents").file(pptx).with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID pptxId = documentIdFrom(pptxLocation);
        awaitReady(pptxId);
        assertThat(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(pptxId))
                .extracting(DocumentChunk::getLocationLabel)
                .containsExactly("Slide 1", "Slide 2", "Slide 3");
    }

    @Test
    void unsupportedAndSpoofedFilesAreRejected() throws Exception {
        AppUserPrincipal student = createStudent("reject-" + UUID.randomUUID() + "@example.com");
        long before = documentRepository.count();

        MockMultipartFile executable = new MockMultipartFile("file", "setup.exe",
                "application/octet-stream", new byte[]{0x4D, 0x5A, 0x00, 0x00});
        mockMvc.perform(multipart("/documents").file(executable).with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/documents"))
                .andExpect(flash().attributeExists("uploadError"));

        MockMultipartFile spoofed = new MockMultipartFile("file", "notes.pdf", "application/pdf",
                DocumentFixtures.jpegHeader());
        mockMvc.perform(multipart("/documents").file(spoofed).with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("uploadError"));

        assertThat(documentRepository.count()).isEqualTo(before);
    }

    @Test
    void documentsAreIsolatedBetweenStudents() throws Exception {
        AppUserPrincipal owner = createStudent("owner-" + UUID.randomUUID() + "@example.com");
        AppUserPrincipal other = createStudent("other-" + UUID.randomUUID() + "@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "private.txt", "text/plain",
                ("Private revision notes about mitosis. Mitosis produces two genetically identical "
                        + "daughter cells and is used for growth and repair. Meiosis produces gametes "
                        + "with half the number of chromosomes. Cell division must be tightly regulated, "
                        + "because uncontrolled division can lead to tumours. ")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String location = mockMvc.perform(multipart("/documents").file(file).with(user(owner)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID documentId = documentIdFrom(location);
        awaitReady(documentId);

        mockMvc.perform(get("/documents/{id}", documentId).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/documents/{id}/status", documentId).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/documents/{id}/delete", documentId).with(user(other)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(documentRepository.findById(documentId)).isPresent();

        mockMvc.perform(get("/documents/{id}", documentId).with(user(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void deletingADocumentRemovesItsChunks() throws Exception {
        AppUserPrincipal student = createStudent("delete-" + UUID.randomUUID() + "@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "delete-me.txt", "text/plain",
                ("Content that will be deleted. This paragraph is long enough to be treated as a "
                        + "meaningful document so that extraction succeeds and the stored chunks can "
                        + "be verified after deletion. Enzymes are reusable catalysts and are not "
                        + "consumed by the reactions they catalyse, so a small amount can act on many "
                        + "substrate molecules. ")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String location = mockMvc.perform(multipart("/documents").file(file).with(user(student)).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        UUID documentId = documentIdFrom(location);
        awaitReady(documentId);
        assertThat(chunkRepository.countByDocumentId(documentId)).isPositive();

        mockMvc.perform(post("/documents/{id}/delete", documentId).with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/documents"));

        assertThat(documentRepository.findById(documentId)).isEmpty();
        assertThat(chunkRepository.countByDocumentId(documentId)).isZero();
    }

    private UUID documentIdFrom(String redirectUrl) {
        assertThat(redirectUrl).isNotNull();
        return UUID.fromString(redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1));
    }

    private Document awaitReady(UUID documentId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new AssertionError("Document disappeared"));
            if (document.getStatus() == DocumentProcessingStatus.READY) {
                return document;
            }
            if (document.getStatus() == DocumentProcessingStatus.FAILED) {
                fail("Extraction failed: " + document.getFailureReason());
            }
            Thread.sleep(150);
        }
        return fail("Document " + documentId + " did not finish processing in time");
    }
}
