package com.examprep.study.repository;

import com.examprep.study.domain.FlashcardReview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, UUID> {

    long countByStudentId(UUID studentId);

    long countByStudyPackIdAndStudentId(UUID studyPackId, UUID studentId);
}
