package com.examprep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application level settings bound from the {@code app.*} configuration namespace.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage,
                            Upload upload,
                            Processing processing,
                            Study study,
                            Tutor tutor) {

    public record Storage(String location) {
    }

    public record Upload(int maxSizeMb) {

        public long maxSizeBytes() {
            return Math.max(1, maxSizeMb) * 1024L * 1024L;
        }
    }

    public record Processing(int staleAfterMinutes,
                             int maxExtractedCharacters,
                             int maxChunkCharacters,
                             int targetChunkCharacters,
                             int minimumMeaningfulCharacters) {
    }

    public record Study(int maxSourceCharactersPerRequest,
                        int sectionBatchCharacters,
                        int defaultQuestionCount,
                        int defaultFlashcardCount,
                        int defaultExamDurationMinutes,
                        int maxQuestionCount,
                        int maxFlashcardCount) {
    }

    public record Tutor(int maxContextChunks) {
    }
}
