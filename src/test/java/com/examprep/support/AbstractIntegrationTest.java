package com.examprep.support;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.examprep.api.dto.UserAccountDto;
import com.examprep.security.SecurityUser;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Integration tests run against a real PostgreSQL server started from embedded binaries, so Flyway,
 * JSONB columns and Hibernate schema validation are exercised exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    protected MockMvc mockMvc;

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start embedded PostgreSQL for tests", exception);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("examprep.storage.location", () -> "target/test-uploads");
        registry.add("examprep.processing.stale-after-minutes", () -> "60");
        registry.add("examprep.ai.api-key", () -> "");
    }

    /** A principal backed by a real account id, used for ownership-aware page requests. */
    protected SecurityUser securityUser(UserAccountDto account) {
        return new SecurityUser(account.id(), account.fullName(), account.email(),
                "$2a$10$abcdefghijklmnopqrstuvwxzy0123456789ABCDEFGHIJKLMNOPQRST");
    }

    protected static void await(Duration timeout, java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(120);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for asynchronous work", exception);
            }
        }
        throw new AssertionError("Condition was not met within " + timeout);
    }
}
