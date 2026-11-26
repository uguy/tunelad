package org.tunelad.live.internal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.tunelad.AbstractApiTest;
import org.tunelad.NatsContainerDefinition;
import org.tunelad.track.TrackDTO;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

/**
 * Live updates are best effort: with NATS unreachable the application starts and works, and live events flow again,
 * without a restart, once NATS is there. NATS is started by the test, on a port that nothing listens on at first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tunelad.test.nats-container=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveResilienceTest extends AbstractApiTest {

    private static final int NATS_PORT = freePort();
    private static GenericContainer<?> nats;

    @DynamicPropertySource
    static void natsNobodyListensOn(DynamicPropertyRegistry registry) {
        registry.add("tunelad.live.nats.url", () -> "nats://localhost:" + NATS_PORT);
    }

    @Autowired LiveBus bus;

    @Test
    @Order(1)
    void the_application_starts_and_publishing_returns_while_nats_is_down() {
        assertThatCode(() -> bus.publish(LiveSubjects.library("track.indexed"), "{\"type\":\"track.indexed\",\"id\":\"1\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(2)
    void an_upload_still_succeeds_and_the_track_is_searchable_while_nats_is_down() {
        var id = uploadOk("Rainfall", "Rain on tin", KNEES_UP_MP3).id();

        await().atMost(Duration.ofSeconds(15)).until(() -> searchTitles("rain").contains("Rainfall"));
        assertThat(id).isNotBlank();
    }

    @Test
    @Order(3)
    void live_events_flow_again_once_nats_is_back() {
        nats = new GenericContainer<>(NatsContainerDefinition.IMAGE)
                .withExposedPorts(NatsContainerDefinition.PORT)
                .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));
        nats.setPortBindings(List.of(NATS_PORT + ":" + NatsContainerDefinition.PORT));
        nats.start();
        var stream = LiveStream.open(client, null);

        // The client reconnects by itself, within a few seconds; events published meanwhile are dropped, so keep trying
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).until(() -> {
            bus.publish(LiveSubjects.library("track.indexed"), "{\"type\":\"track.indexed\",\"id\":\"probe\"}");
            return stream.dataOf("track.indexed").stream().anyMatch(data -> data.contains("probe"));
        });

        // And the real path works too
        var id = uploadOk("Downpour", "Rain on tin, louder", MOSSY_BANKS_MP3).id();
        await().atMost(Duration.ofSeconds(20)).until(() ->
                stream.dataOf("waveform.computed").stream().anyMatch(data -> data.contains(id)));
        stream.close();
    }

    @AfterAll
    static void stopNats() {
        if (nats != null) {
            nats.stop();
        }
    }

    private List<String> searchTitles(String query) {
        return client.get().uri("/api/tracks/search?q={q}", query).exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody().stream().map(TrackDTO::title).toList();
    }

    private static int freePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
