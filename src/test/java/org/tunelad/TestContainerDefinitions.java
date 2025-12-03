package org.tunelad;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mongodb.MongoDBContainer;

public interface TestContainerDefinitions {

    @Container
    @ServiceConnection(name = "mongo")
    MongoDBContainer mongoContainer = new MongoDBContainer("mongo:latest");
}
