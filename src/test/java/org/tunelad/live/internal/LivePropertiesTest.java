package org.tunelad.live.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LivePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LiveConfig.class);

    @Test
    void defaultsToALocalNatsAndAFifteenSecondHeartbeat() {
        runner.run(context -> {
            var properties = context.getBean(LiveProperties.class);
            assertThat(properties.nats().url()).isEqualTo("nats://localhost:4222");
            assertThat(properties.heartbeat()).isEqualTo(Duration.ofSeconds(15));
        });
    }

    @Test
    void bindsAnotherNatsUrlAndHeartbeat() {
        runner.withPropertyValues("tunelad.live.nats.url=nats://nats.example:4333", "tunelad.live.heartbeat=2s")
                .run(context -> {
                    var properties = context.getBean(LiveProperties.class);
                    assertThat(properties.nats().url()).isEqualTo("nats://nats.example:4333");
                    assertThat(properties.heartbeat()).isEqualTo(Duration.ofSeconds(2));
                });
    }
}
