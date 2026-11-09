package com.examprep.web.page.form;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Study package generation options submitted from the document page.
 */
public class StudyOptionsForm {

    private String language = "English";

    private String academicLevel = "Undergraduate";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate examDate;

    private Integer questionCount;

    private Integer flashcardCount;

    private Integer examDurationMinutes;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAcademicLevel() {
        return academicLevel;
    }

    public void setAcademicLevel(String academicLevel) {
        this.academicLevel = academicLevel;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public Integer getFlashcardCount() {
        return flashcardCount;
    }

    public void setFlashcardCount(Integer flashcardCount) {
        this.flashcardCount = flashcardCount;
    }

    public Integer getExamDurationMinutes() {
        return examDurationMinutes;
    }

    public void setExamDurationMinutes(Integer examDurationMinutes) {
        this.examDurationMinutes = examDurationMinutes;
    }
}
