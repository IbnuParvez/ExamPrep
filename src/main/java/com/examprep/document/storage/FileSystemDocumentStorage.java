package com.examprep.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.examprep.common.NotFoundException;
import com.examprep.common.ValidationException;
import com.examprep.config.StorageProperties;

/**
 * Stores uploads in a private directory outside the web root. Internal filenames are random
 * identifiers; the student's original filename is kept only as database metadata.
 */
@Component
public class FileSystemDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDocumentStorage.class);

    private final Path root;

    public FileSystemDocumentStorage(StorageProperties properties) throws IOException {
        this.root = properties.getLocation().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(InputStream inputStream, String extension) throws IOException {
        String key = UUID.randomUUID().toString().replace("-", "") + "." + sanitizeExtension(extension);
        Path target = resolve(key);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    @Override
    public Path load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("The stored file for this document is no longer available.");
        }
        return path;
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException | RuntimeException exception) {
            // Deleting the database row must still succeed if the file is already gone.
            log.warn("Could not delete stored document file: {}", exception.getClass().getSimpleName());
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new ValidationException("Invalid storage reference.");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ValidationException("Invalid storage reference.");
        }
        return resolved;
    }

    private String sanitizeExtension(String extension) {
        if (extension == null || !extension.matches("[a-zA-Z0-9]{1,10}")) {
            throw new ValidationException("Unsupported file extension.");
        }
        return extension.toLowerCase();
    }
}
