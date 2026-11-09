package com.examprep.document.repository;

import com.examprep.document.domain.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    long countByDocumentId(UUID documentId);

    @Modifying
    @Query("delete from DocumentChunk c where c.documentId = :documentId")
    int deleteAllForDocument(@Param("documentId") UUID documentId);
}
