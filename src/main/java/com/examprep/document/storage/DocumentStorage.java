package com.examprep.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Private binary storage for uploaded documents. Keys are generated, never client-supplied. */
public interface DocumentStorage {

    String store(InputStream inputStream, String extension) throws IOException;

    Path load(String storageKey);

    void delete(String storageKey);
}
