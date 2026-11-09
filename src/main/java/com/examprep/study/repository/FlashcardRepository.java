package com.examprep.study.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.study.domain.FlashcardEntity;

public interface FlashcardRepository extends JpaRepository<FlashcardEntity, UUID> {

    List<FlashcardEntity> findByStudyPackIdOrderByOrdinalAsc(UUID studyPackId);

    long countByStudyPackId(UUID studyPackId);

    @Modifying
    @Transactional
    @Query("delete from FlashcardEntity flashcard where flashcard.studyPackId = :studyPackId")
    void deleteAllForPack(@Param("studyPackId") UUID studyPackId);
}
