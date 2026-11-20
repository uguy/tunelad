package org.tunelad;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/** What every application test needs around it: MongoDB, and a stand-in for the identity provider. */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestSecurityConfiguration.class)
public class TestContainerDefinitions {

    @Bean
    @ServiceConnection
    public MongoDBContainer mongoContainer() {
        // A replica set, because the application uses MongoDB transactions
        return new MongoDBContainer("mongo:7.0")
                .withReplicaSet()
                .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES));
    }
}
