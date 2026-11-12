package com.sanitation.management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies the Spring context wires up.
 * Database auto-configuration is excluded so the test needs no running MySQL.
 * Remove the exclusions once entities exist and you add a proper test database strategy.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class SanitationManagementApplicationTests {

    @Test
    void contextLoads() {
    }
}
