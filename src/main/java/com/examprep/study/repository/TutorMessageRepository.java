package com.examprep.study.repository;

import com.examprep.study.domain.TutorMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TutorMessageRepository extends JpaRepository<TutorMessage, UUID> {

    List<TutorMessage> findByStudyPackIdOrderByCreatedAtAsc(UUID studyPackId);

    long countByStudyPackId(UUID studyPackId);
}
