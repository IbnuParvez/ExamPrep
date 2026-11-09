package com.examprep;

import static org.assertj.core.api.Assertions.assertThat;

import com.examprep.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ExamPrepApplicationTests extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndMigrationsApply() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isEqualTo(3);
    }

    @Test
    void schemaMatchesEntityMappings() {
        Integer columns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = 'public'
                   and table_name in ('app_user', 'document', 'document_chunk', 'study_pack',
                                      'flashcard', 'question', 'quiz_attempt', 'quiz_answer',
                                      'tutor_message', 'flashcard_review')
                """, Integer.class);
        assertThat(columns).isGreaterThan(50);
    }
}
