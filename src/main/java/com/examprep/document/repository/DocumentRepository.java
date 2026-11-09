package com.examprep.document.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.api.model.DocumentStatus;
import com.examprep.document.domain.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Document> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Document> findTop5ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    @Modifying
    @Transactional
    @Query("""
            update Document d
               set d.status = :failed, d.failureReason = :reason, d.updatedAt = :now
             where d.status in :statuses and d.updatedAt < :cutoff
            """)
    int markStaleJobsFailed(@Param("failed") DocumentStatus failed,
                            @Param("statuses") List<DocumentStatus> statuses,
                            @Param("cutoff") Instant cutoff,
                            @Param("reason") String reason,
                            @Param("now") Instant now);
}
