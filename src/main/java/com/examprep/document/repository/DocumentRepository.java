package com.examprep.document.repository;

import com.examprep.api.document.DocumentProcessingStatus;
import com.examprep.document.domain.Document;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, DocumentProcessingStatus status);

    long countByStatus(DocumentProcessingStatus status);

    @Modifying
    @Query("""
            update Document d
               set d.status = :failedStatus,
                   d.failureReason = :reason,
                   d.updatedAt = :now
             where d.status in :statuses
            """)
    int markInterrupted(@Param("statuses") List<DocumentProcessingStatus> statuses,
                        @Param("failedStatus") DocumentProcessingStatus failedStatus,
                        @Param("reason") String reason,
                        @Param("now") Instant now);
}
