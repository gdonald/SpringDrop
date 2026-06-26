package dev.springdrop.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Proves the {@code @DataJpaTest} slice runs against the real PostgreSQL
 * container rather than an embedded database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseConnectivityTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void runsAgainstPostgres() {
        Object version = entityManager
                .createNativeQuery("select version()")
                .getSingleResult();

        assertThat(version.toString()).contains("PostgreSQL");
    }
}
