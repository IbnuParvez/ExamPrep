package com.examprep.document.storage;

import com.examprep.common.StorageException;
import com.examprep.config.AppProperties;
import com.examprep.document.domain.DocumentFormat;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded documents outside the web root using generated identifiers. Client supplied
 * file names are never used as paths.
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private final Path root;

    public DocumentStorageService(AppProperties properties) {
        this.root = Paths.get(properties.storage().location()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepareStorageDirectory() {
        try {
            Files.createDirectories(root);
            log.info("Document storage directory ready at {}", root);
        } catch (IOException failure) {
            throw new StorageException("The document storage directory could not be created.", failure);
        }
    }

    public StoredFile store(MultipartFile file, UUID ownerId, DocumentFormat format) {
        String storageKey = ownerId + "/" + UUID.randomUUID() + "." + format.extension();
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(target);
            return new StoredFile(storageKey, size);
        } catch (IOException failure) {
            throw new StorageException("The file could not be saved. Please try again.", failure);
        }
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StorageException("The stored file reference is invalid.");
        }
        Path candidate = root.resolve(storageKey).normalize();
        if (!candidate.startsWith(root)) {
            throw new StorageException("The stored file reference is invalid.");
        }
        return candidate;
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException | StorageException failure) {
            log.warn("Could not remove stored file for a deleted document: {}", failure.getClass().getSimpleName());
        }
    }
}
