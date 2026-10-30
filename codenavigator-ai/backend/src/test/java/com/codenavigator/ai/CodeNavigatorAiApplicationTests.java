package com.codenavigator.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies the entire Spring application context boots without errors,
 * i.e. all beans wire up correctly (config classes, datasource, component scan).
 * This is the single most valuable "did I break something" test in the whole project.
 */
@SpringBootTest
@ActiveProfiles("test") // H2 in-memory - tests never depend on a running MySQL instance
class CodeNavigatorAiApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: if the ApplicationContext fails to start, this test fails.
    }
}
