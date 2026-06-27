package dev.springdrop.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need a real database. A single PostgreSQL container is
 * started once and shared across every test class through the static field, and
 * its connection details are injected via {@link ServiceConnection}. Tests run
 * against real PostgreSQL so dynamic DDL and raw SQL behave as in production.
 */
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        POSTGRES.start();
    }

    // Many cached @SpringBootTest contexts share this one container; keep each
    // context's connection pool small so their total stays under Postgres'
    // connection limit.
    @DynamicPropertySource
    static void datasourcePool(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
    }
}
