package org.tunelad.identification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.track.IdentificationState;
import org.tunelad.track.PerformanceDTO;
import org.tunelad.track.TrackDTO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Tests for openspec/specs/identification: one test per scenario, against a stub of the analysis sidecar. */
class IdentificationApiTest extends AbstractApiTest {

    /** What the stub sidecar answers, and saw. */
    static final class Sidecar {
        volatile int status = 200;
        volatile String body = "[]";
        volatile long delayMillis = 0;
        final List<String> tunesAsked = new CopyOnWriteArrayList<>();
        final List<Integer> audioBytes = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        void reset() {
            status = 200;
            body = "[]";
            delayMillis = 0;
            tunesAsked.clear();
            audioBytes.clear();
            calls.set(0);
        }

        void hears(String json) {
            body = json;
        }
    }

    static final Sidecar SIDECAR = new Sidecar();
    static final HttpServer SERVER = startStub();

    private static HttpServer startStub() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/identify", exchange -> {
                var audio = exchange.getRequestBody().readAllBytes();
                var query = exchange.getRequestURI().getRawQuery();
                SIDECAR.tunesAsked.add(URLDecoder.decode(query.substring(query.indexOf('=') + 1), StandardCharsets.UTF_8));
                SIDECAR.audioBytes.add(audio.length);
                SIDECAR.calls.incrementAndGet();
                try {
                    Thread.sleep(SIDECAR.delayMillis);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                var bytes = SIDECAR.body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("content-type", "application/json");
                exchange.sendResponseHeaders(SIDECAR.status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void sidecar(DynamicPropertyRegistry registry) {
        registry.add("tunelad.identification.url", () -> "http://localhost:" + SERVER.getAddress().getPort());
        registry.add("tunelad.identification.timeout", () -> "5s");
    }

    @AfterAll
    static void stopStub() {
        SERVER.stop(0);
    }

    private static final String COOLEYS_SOURCE = "1001";
    private static final String KESH_SOURCE = "1002";

    private String cooleys;
    private String kesh;

    @BeforeEach
    void setUp() {
        SIDECAR.reset();
        cooleys = createTune("Cooley's", "reel");
        kesh = createTune("The Kesh", "jig");
        sourceId(cooleys, COOLEYS_SOURCE);
        sourceId(kesh, KESH_SOURCE);
    }

    private void sourceId(String tuneId, String sourceId) {
        db.sql("UPDATE tune_tunes SET source_id = :source WHERE id = :id")
                .bind("source", Integer.parseInt(sourceId)).bind("id", tuneId).then().block();
    }

    private TrackDTO uploadSet() {
        var parts = new HashMap<String, String>();
        parts.put("title", "Knees Up");
        parts.put("description", "A reel and a jig");
        parts.put("setId", createSet(cooleys, kesh).id());
        return upload(parts, KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO get(String id) {
        return client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private static String detection(String sourceId, double start) {
        return "{\"tuneId\":\"" + sourceId + "\",\"name\":\"x\",\"start\":" + start + ",\"end\":" + (start + 60) + ",\"confidence\":0.6}";
    }

    private void awaitCalls(int calls) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(SIDECAR.calls.get()).isGreaterThanOrEqualTo(calls));
    }

    /** The sidecar has answered and the Track had time to take the answer in. */
    private void settle(int calls) {
        awaitCalls(calls);
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<IdentificationState> states(TrackDTO track) {
        return track.performances().stream().map(PerformanceDTO::identification).toList();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec rerun(String id, String token) {
        return client.post().uri("/api/tracks/{id}/identification", id)
                .headers(headers -> {
                    if (token != null) headers.setBearerAuth(token);
                }).exchange();
    }

    @Test
    void upload_returns_before_analysis_ends() {
        SIDECAR.delayMillis = 3_000;
        SIDECAR.hears("[" + detection(KESH_SOURCE, 95.5) + "]");

        var started = System.nanoTime();
        var track = uploadSet();
        var took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(took).isLessThan(Duration.ofSeconds(3));
        assertThat(states(track)).containsOnly(IdentificationState.PENDING);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsExactly(IdentificationState.PENDING, IdentificationState.IDENTIFIED));
    }

    @Test
    void proposal_accepted() {
        SIDECAR.hears("[" + detection(KESH_SOURCE, 95.5) + "]");

        var track = uploadSet();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsExactly(IdentificationState.PENDING, IdentificationState.IDENTIFIED));
        var performances = get(track.id()).performances();
        assertThat(performances.get(1).startOffsetSeconds()).isEqualTo(95.5);
        assertThat(performances.get(0).startOffsetSeconds()).isZero();
        // The sidecar was asked for the thesession.org ids of the Set's tunes, and got the audio
        assertThat(SIDECAR.tunesAsked.getFirst().split(",")).containsExactlyInAnyOrder(COOLEYS_SOURCE, KESH_SOURCE);
        assertThat(SIDECAR.audioBytes.getFirst()).isPositive();
    }

    @Test
    void tune_outside_the_set() {
        SIDECAR.hears("[" + detection("999999", 12) + "]");

        var track = uploadSet();
        settle(1);

        assertThat(states(get(track.id()))).containsOnly(IdentificationState.PENDING);
        assertThat(get(track.id()).performances()).extracting(PerformanceDTO::startOffsetSeconds).containsOnly(0.0);
    }

    @Test
    void nothing_detected() {
        var track = uploadSet();
        settle(1);

        assertThat(states(get(track.id()))).containsOnly(IdentificationState.PENDING);
    }

    @Test
    void tunes_without_a_thesession_id_are_not_analysed() {
        db.sql("UPDATE tune_tunes SET source_id = NULL").then().block();

        var track = uploadSet();
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(SIDECAR.calls.get()).isZero();
        assertThat(states(get(track.id()))).containsOnly(IdentificationState.PENDING);
    }

    @Test
    void service_down() {
        SIDECAR.status = 503;
        SIDECAR.hears("{\"error\":\"down\"}");

        var track = uploadSet();
        settle(1);

        assertThat(get(track.id()).title()).isEqualTo("Knees Up");
        assertThat(states(get(track.id()))).containsOnly(IdentificationState.PENDING);

        // The sidecar is back: a re-run works
        SIDECAR.status = 200;
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 3) + "," + detection(KESH_SOURCE, 95.5) + "]");
        rerun(track.id(), UPLOADER_TOKEN).expectStatus().isAccepted();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsOnly(IdentificationState.IDENTIFIED));
    }

    @Test
    void rerun_keeps_a_correction() {
        SIDECAR.hears("[" + detection(KESH_SOURCE, 95.5) + "]");
        var track = uploadSet();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id())).get(1)).isEqualTo(IdentificationState.IDENTIFIED));
        edit(track.id(), Map.of("title", "Knees Up", "description", "A reel and a jig",
                "performances", List.of(Map.of(), Map.of("startOffsetSeconds", 90))), UPLOADER_TOKEN).expectStatus().isOk();
        var before = SIDECAR.calls.get();
        SIDECAR.hears("[" + detection(KESH_SOURCE, 101) + "]");

        rerun(track.id(), UPLOADER_TOKEN).expectStatus().isAccepted();
        settle(before + 1);

        var stored = get(track.id()).performances().get(1);
        assertThat(stored.startOffsetSeconds()).isEqualTo(90);
        assertThat(stored.identification()).isEqualTo(IdentificationState.CORRECTED);
    }

    @Test
    void owner_reruns() {
        var track = uploadSet();
        settle(1);
        SIDECAR.hears("[" + detection(KESH_SOURCE, 95.5) + "]");

        rerun(track.id(), UPLOADER_TOKEN).expectStatus().isAccepted();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(get(track.id()).performances().get(1).startOffsetSeconds()).isEqualTo(95.5));
    }

    @Test
    void administrator_reruns() {
        var track = uploadSet();
        settle(1);

        rerun(track.id(), TestTokens.admin()).expectStatus().isAccepted();
        awaitCalls(2);
    }

    @Test
    void not_the_owner() {
        var track = uploadSet();
        settle(1);

        rerun(track.id(), TestTokens.bearer("bob", "Bob Smith")).expectStatus().isForbidden();

        assertThat(SIDECAR.calls.get()).isEqualTo(1);
    }

    @Test
    void unknown_track_and_anonymous() {
        rerun("no-such-track", UPLOADER_TOKEN).expectStatus().isNotFound();
        rerun("no-such-track", null).expectStatus().isUnauthorized();
    }
}
