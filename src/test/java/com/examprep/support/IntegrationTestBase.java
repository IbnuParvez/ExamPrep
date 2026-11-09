package com.examprep.support;

import com.examprep.security.AppUserPrincipal;
import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test base backed by a real PostgreSQL server started from embedded binaries. This
 * keeps Flyway migrations, JSONB columns and Hibernate validation honest without requiring Docker
 * in the build environment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // shutting down
                }
            }));
            return postgres;
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not start the embedded PostgreSQL server", failure);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserAccountRepository userAccountRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected AppUserPrincipal createStudent(String email) {
        return createStudent(email, "Test Student");
    }

    protected AppUserPrincipal createStudent(String email, String fullName) {
        UserAccount account = UserAccount.create(fullName, email.toLowerCase(java.util.Locale.ROOT),
                passwordEncoder.encode("password123"));
        UserAccount saved = userAccountRepository.save(account);
        return new AppUserPrincipal(saved.getId(), saved.getEmail(), saved.getFullName(),
                saved.getPasswordHash(), true);
    }
}
