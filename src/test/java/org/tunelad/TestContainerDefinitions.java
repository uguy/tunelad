package org.tunelad;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerDefinitions {

    @Bean
    @ServiceConnection
    public MongoDBContainer mongoContainer() {
        return new MongoDBContainer("mongo:7.0")
                .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES));
    }
}
