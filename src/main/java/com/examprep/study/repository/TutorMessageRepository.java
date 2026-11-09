package com.examprep.study.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examprep.study.domain.TutorMessageEntity;

public interface TutorMessageRepository extends JpaRepository<TutorMessageEntity, UUID> {

    List<TutorMessageEntity> findByStudyPackIdOrderByCreatedAtAsc(UUID studyPackId);

    long countByStudyPackId(UUID studyPackId);
}
