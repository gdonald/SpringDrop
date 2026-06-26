package dev.springdrop;

import dev.springdrop.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringDropApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if the application context cannot start against PostgreSQL.
    }
}
