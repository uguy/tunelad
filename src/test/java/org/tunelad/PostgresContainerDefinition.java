package org.tunelad;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * PostgreSQL for application tests, serving both the R2DBC and the JDBC connection. Most tests import it through
 * {@link TestContainerDefinitions}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerDefinition {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17")
                .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES));
    }
}
