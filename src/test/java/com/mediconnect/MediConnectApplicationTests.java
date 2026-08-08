package com.mediconnect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring application context loads successfully.
 * Requires a running MySQL instance (docker-compose) when run with
 * {@code @SpringBootTest}. For CI without a DB, use the {@code test} profile
 * with an embedded datasource (H2) — to be added in Phase 1.
 */
@SpringBootTest
@ActiveProfiles("test")
class MediConnectApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the application context starts without errors.
    }
}
