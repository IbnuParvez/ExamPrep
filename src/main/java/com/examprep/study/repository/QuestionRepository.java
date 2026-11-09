package com.examprep.study.repository;

import com.examprep.study.domain.Question;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByStudyPackIdOrderByPositionAsc(UUID studyPackId);

    List<Question> findByStudyPackIdAndExamEligibleTrueOrderByPositionAsc(UUID studyPackId);

    Optional<Question> findByIdAndStudyPackId(UUID id, UUID studyPackId);

    long countByStudyPackId(UUID studyPackId);

    long countByStudyPackIdAndExamEligibleTrue(UUID studyPackId);

    @Modifying
    @Query("delete from Question q where q.studyPackId = :studyPackId")
    int deleteAllForPack(@Param("studyPackId") UUID studyPackId);
}
