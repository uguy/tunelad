package org.tunelad;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

/**
 * NATS for application tests, where live UI updates travel between instances. Most tests import it through
 * {@link TestContainerDefinitions}. Spring Boot has no service connection for NATS, so the URL is registered as the
 * application property. A test that wants to choose the URL itself (to have NATS unreachable, say) sets
 * {@code tunelad.test.nats-container=false}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class NatsContainerDefinition {

    public static final String IMAGE = "nats:2.11-alpine";
    public static final int PORT = 4222;

    @Bean
    @ConditionalOnProperty(prefix = "tunelad.test", name = "nats-container", havingValue = "true", matchIfMissing = true)
    public GenericContainer<?> natsContainer() {
        return new GenericContainer<>(IMAGE)
                .withExposedPorts(PORT)
                .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    @Bean
    @ConditionalOnProperty(prefix = "tunelad.test", name = "nats-container", havingValue = "true", matchIfMissing = true)
    public DynamicPropertyRegistrar natsUrl(GenericContainer<?> natsContainer) {
        return registry -> registry.add("tunelad.live.nats.url",
                () -> "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(PORT));
    }
}
