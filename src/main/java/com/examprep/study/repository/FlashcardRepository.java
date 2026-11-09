package com.examprep.study.repository;

import com.examprep.study.domain.Flashcard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashcardRepository extends JpaRepository<Flashcard, UUID> {

    List<Flashcard> findByStudyPackIdOrderByPositionAsc(UUID studyPackId);

    Optional<Flashcard> findByIdAndStudyPackId(UUID id, UUID studyPackId);

    long countByStudyPackId(UUID studyPackId);

    @Modifying
    @Query("delete from Flashcard f where f.studyPackId = :studyPackId")
    int deleteAllForPack(@Param("studyPackId") UUID studyPackId);
}
