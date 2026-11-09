package com.examprep.study.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.api.model.StudyPackStatus;
import com.examprep.study.domain.StudyPack;

public interface StudyPackRepository extends JpaRepository<StudyPack, UUID> {

    Optional<StudyPack> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<StudyPack> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<StudyPack> findTop5ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<StudyPack> findAllByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    long countByOwnerId(UUID ownerId);

    @Modifying
    @Transactional
    @Query("""
            update StudyPack p
               set p.status = :failed, p.failureReason = :reason, p.progressMessage = null,
                   p.progressPercent = 0, p.updatedAt = :now
             where p.status in :statuses and p.updatedAt < :cutoff
            """)
    int markStaleJobsFailed(@Param("failed") StudyPackStatus failed,
                            @Param("statuses") List<StudyPackStatus> statuses,
                            @Param("cutoff") Instant cutoff,
                            @Param("reason") String reason,
                            @Param("now") Instant now);
}
