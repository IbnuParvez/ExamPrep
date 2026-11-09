package com.examprep.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.document.domain.DocumentChunkEntity;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    List<DocumentChunkEntity> findByDocumentIdOrderByOrdinalAsc(UUID documentId);

    long countByDocumentId(UUID documentId);

    @Modifying
    @Transactional
    @Query("delete from DocumentChunkEntity chunk where chunk.documentId = :documentId")
    void deleteAllForDocument(@Param("documentId") UUID documentId);
}
