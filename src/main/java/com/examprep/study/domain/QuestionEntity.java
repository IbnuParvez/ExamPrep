package com.examprep.study.domain;

import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.examprep.api.model.Difficulty;
import com.examprep.api.model.QuestionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A practice/examination question. Answers live here and are never included in an exam DTO before
 * the student submits.
 */
@Entity
@Table(name = "questions")
public class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    @Column(name = "correct_option_index")
    private Integer correctOptionIndex;

    @Column(name = "correct_boolean")
    private Boolean correctBoolean;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(columnDefinition = "text")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "source_ref", length = 200)
    private String sourceRef;

    @Column(name = "exam_eligible", nullable = false)
    private boolean examEligible;

    protected QuestionEntity() {
    }

    public static QuestionEntity of(UUID studyPackId, int ordinal, QuestionType type, String questionText,
                                    List<String> options, Integer correctOptionIndex, Boolean correctBoolean,
                                    String answerText, String explanation, Difficulty difficulty,
                                    String sourceRef) {
        QuestionEntity entity = new QuestionEntity();
        entity.studyPackId = studyPackId;
        entity.ordinal = ordinal;
        entity.type = type;
        entity.questionText = questionText;
        entity.options = options;
        entity.correctOptionIndex = correctOptionIndex;
        entity.correctBoolean = correctBoolean;
        entity.answerText = answerText;
        entity.explanation = explanation;
        entity.difficulty = difficulty;
        entity.sourceRef = sourceRef;
        entity.examEligible = type.isAutoScored();
        return entity;
    }

    /** Display form of the correct answer. */
    public String correctAnswerText() {
        return switch (type) {
            case MULTIPLE_CHOICE -> options == null || correctOptionIndex == null
                    || correctOptionIndex < 0 || correctOptionIndex >= options.size()
                    ? ""
                    : options.get(correctOptionIndex);
            case TRUE_FALSE -> Boolean.TRUE.equals(correctBoolean) ? "True" : "False";
            case SHORT_ANSWER -> answerText == null ? "" : answerText;
        };
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public QuestionType getType() {
        return type;
    }

    public String getQuestionText() {
        return questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public Integer getCorrectOptionIndex() {
        return correctOptionIndex;
    }

    public Boolean getCorrectBoolean() {
        return correctBoolean;
    }

    public String getAnswerText() {
        return answerText;
    }

    public String getExplanation() {
        return explanation;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public boolean isExamEligible() {
        return examEligible;
    }
}
