package com.examprep.study.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.examprep.study.domain.FlashcardReview;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, UUID> {

    List<FlashcardReview> findByOwnerIdOrderByReviewedAtDesc(UUID ownerId, Pageable pageable);

    List<FlashcardReview> findByStudyPackIdOrderByReviewedAtDesc(UUID studyPackId);

    long countByOwnerId(UUID ownerId);
}
