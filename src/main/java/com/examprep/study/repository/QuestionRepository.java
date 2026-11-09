package com.examprep.study.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.study.domain.QuestionEntity;

public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID> {

    List<QuestionEntity> findByStudyPackIdOrderByOrdinalAsc(UUID studyPackId);

    List<QuestionEntity> findByStudyPackIdAndExamEligibleTrueOrderByOrdinalAsc(UUID studyPackId);

    long countByStudyPackId(UUID studyPackId);

    long countByStudyPackIdAndExamEligibleTrue(UUID studyPackId);

    java.util.Optional<QuestionEntity> findByIdAndStudyPackId(UUID id, UUID studyPackId);

    @Modifying
    @Transactional
    @Query("delete from QuestionEntity question where question.studyPackId = :studyPackId")
    void deleteAllForPack(@Param("studyPackId") UUID studyPackId);
}
