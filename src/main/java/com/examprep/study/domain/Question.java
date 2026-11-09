package com.examprep.study.domain;

import com.examprep.api.study.Difficulty;
import com.examprep.api.study.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A practice or examination question. The correct answer lives only here on the server; templates
 * and JSON responses never expose it before submission.
 */
@Entity
@Table(name = "question")
public class Question {

    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType type;

    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options")
    private List<String> options;

    @Column(name = "correct_answer", nullable = false, columnDefinition = "text")
    private String correctAnswer;

    @Column(name = "explanation", nullable = false, columnDefinition = "text")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "source_reference", length = 160)
    private String sourceReference;

    @Column(name = "exam_eligible", nullable = false)
    private boolean examEligible;

    protected Question() {
        // for JPA
    }

    public Question(UUID studyPackId, int position, QuestionType type, String prompt, List<String> options,
                    String correctAnswer, String explanation, Difficulty difficulty,
                    String sourceReference, boolean examEligible) {
        this.id = UUID.randomUUID();
        this.studyPackId = studyPackId;
        this.position = position;
        this.type = type;
        this.prompt = prompt;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.difficulty = difficulty;
        this.sourceReference = sourceReference;
        this.examEligible = examEligible;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public int getPosition() {
        return position;
    }

    public QuestionType getType() {
        return type;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getOptions() {
        return options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public boolean isExamEligible() {
        return examEligible;
    }
}
