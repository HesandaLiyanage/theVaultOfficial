package com.hess.thevault;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full Spring context, which requires Postgres on 5433 and Redis
 * on 6379 (see docker-compose.yml at the repo root). Disabled in normal
 * `mvn test` runs; flip the @Disabled off after `docker-compose up -d` to
 * run the smoke test locally.
 */
@SpringBootTest
@ActiveProfiles("stub")
@Disabled("Requires Postgres + Redis. Run `docker-compose up -d` first, then remove this annotation.")
class TheVaultApplicationTests {

    @Test
    void contextLoads() {
    }
}
