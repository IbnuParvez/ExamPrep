package com.examprep.web.form;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.examprep.api.dto.StudyPreferences;

/** Study generation options submitted from the browser. */
public class StudyPreferencesForm {

    private String language = "English";
    private String academicLevel = "Undergraduate";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate examDate;

    private int questionCount = 12;
    private int flashcardCount = 16;
    private int examQuestionCount = 10;
    private int examDurationMinutes = 15;

    public StudyPreferences toPreferences() {
        return new StudyPreferences(language, academicLevel, examDate, questionCount, flashcardCount,
                examQuestionCount, examDurationMinutes);
    }

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

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public int getFlashcardCount() {
        return flashcardCount;
    }

    public void setFlashcardCount(int flashcardCount) {
        this.flashcardCount = flashcardCount;
    }

    public int getExamQuestionCount() {
        return examQuestionCount;
    }

    public void setExamQuestionCount(int examQuestionCount) {
        this.examQuestionCount = examQuestionCount;
    }

    public int getExamDurationMinutes() {
        return examDurationMinutes;
    }

    public void setExamDurationMinutes(int examDurationMinutes) {
        this.examDurationMinutes = examDurationMinutes;
    }
}
