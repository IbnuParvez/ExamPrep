package com.examprep.study.repository;

import com.examprep.api.study.StudyPackStatus;
import com.examprep.study.domain.StudyPack;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPackRepository extends JpaRepository<StudyPack, UUID> {

    Optional<StudyPack> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<StudyPack> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<StudyPack> findTop5ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, StudyPackStatus status);

    @Modifying
    @Query("""
            update StudyPack p
               set p.status = :failedStatus,
                   p.failureReason = :reason,
                   p.progressMessage = null,
                   p.updatedAt = :now
             where p.status in :statuses
            """)
    int markInterrupted(@Param("statuses") List<StudyPackStatus> statuses,
                        @Param("failedStatus") StudyPackStatus failedStatus,
                        @Param("reason") String reason,
                        @Param("now") Instant now);
}
