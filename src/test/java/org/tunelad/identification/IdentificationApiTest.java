package org.tunelad.identification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.tunelad.AbstractApiTest;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AttachSet;
import org.tunelad.tune.DanceType;
import org.tunelad.tune.internal.TuneSource;
import reactor.core.publisher.Mono;
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
@Import(IdentificationApiTest.StubSource.class)
class IdentificationApiTest extends AbstractApiTest {

    /** thesession.org knows tune 27, Cooley's, and 28, The Kesh; it fails for 500 and does not know the rest. */
    @TestConfiguration
    static class StubSource {
        @Bean
        @Primary
        TuneSource tuneSource() {
            return id -> switch (id) {
                case 27 -> Mono.just(new TuneSource.SourceTune(27, "Cooley's", List.of(), null, DanceType.REEL, "Edorian", null));
                case 28 -> Mono.just(new TuneSource.SourceTune(28, "The Kesh", List.of(), null, DanceType.JIG, "Gmajor", null));
                case 500 -> Mono.error(new TuneSource.SourceUnavailableException("down", null));
                default -> Mono.empty();
            };
        }
    }

    @Autowired
    TrackService trackService;

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

    /** What the stub records when it is asked for every tune, with no list. */
    static final String ANY = "<any>";

    static final Sidecar SIDECAR = new Sidecar();
    static final HttpServer SERVER = startStub();

    private static HttpServer startStub() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/identify", exchange -> {
                var audio = exchange.getRequestBody().readAllBytes();
                var query = exchange.getRequestURI().getRawQuery();
                SIDECAR.tunesAsked.add(query == null ? ANY : URLDecoder.decode(query.substring(query.indexOf('=') + 1), StandardCharsets.UTF_8));
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
                assertThat(states(get(track.id()))).containsExactly(IdentificationState.NOTHING_FOUND, IdentificationState.IDENTIFIED));
    }

    @Test
    void proposal_accepted() {
        SIDECAR.hears("[" + detection(KESH_SOURCE, 95.5) + "]");

        var track = uploadSet();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsExactly(IdentificationState.NOTHING_FOUND, IdentificationState.IDENTIFIED));
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

        assertThat(states(get(track.id()))).containsOnly(IdentificationState.NOTHING_FOUND);
        assertThat(get(track.id()).performances()).extracting(PerformanceDTO::startOffsetSeconds).containsOnly(0.0);
    }

    @Test
    void nothing_detected() {
        var track = uploadSet();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsOnly(IdentificationState.NOTHING_FOUND));
    }

    @Test
    void a_later_run_can_still_find_it() {
        var track = uploadSet();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsOnly(IdentificationState.NOTHING_FOUND));
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 3) + "," + detection(KESH_SOURCE, 95.5) + "]");

        rerun(track.id(), UPLOADER_TOKEN).expectStatus().isAccepted();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsOnly(IdentificationState.IDENTIFIED));
    }

    @Test
    void a_run_that_no_longer_hears_an_identified_tune_keeps_it() {
        SIDECAR.hears("[" + detection(KESH_SOURCE, 95.5) + "]");
        var track = uploadSet();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id())).get(1)).isEqualTo(IdentificationState.IDENTIFIED));
        var before = SIDECAR.calls.get();
        SIDECAR.hears("[]");

        rerun(track.id(), UPLOADER_TOKEN).expectStatus().isAccepted();
        settle(before + 1);

        var kesh = get(track.id()).performances().get(1);
        assertThat(kesh.identification()).isEqualTo(IdentificationState.IDENTIFIED);
        assertThat(kesh.startOffsetSeconds()).isEqualTo(95.5);
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
        // Never analysed, so not "nothing found" either
        assertThat(states(get(track.id()))).containsOnly(IdentificationState.PENDING);
    }

    @Test
    void only_the_tunes_that_were_looked_for_are_nothing_found() {
        sourceId(kesh, KESH_SOURCE);
        db.sql("UPDATE tune_tunes SET source_id = NULL WHERE id = :id").bind("id", cooleys).then().block();
        SIDECAR.hears("[]");

        var track = uploadSet();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(states(get(track.id()))).containsExactly(IdentificationState.PENDING, IdentificationState.NOTHING_FOUND));
        assertThat(SIDECAR.tunesAsked.getFirst()).isEqualTo(KESH_SOURCE);
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

    // --- Requirement: The Set of a Track without one is derived from its audio ----------------------

    private TrackDTO uploadWithoutSet() {
        // A null setId omits the part: uploads otherwise get a default Set
        var parts = new HashMap<String, String>();
        parts.put("title", "Knees Up");
        parts.put("setId", null);
        return upload(parts, KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private static String detection(String sourceId, double start, double confidence) {
        return "{\"tuneId\":\"" + sourceId + "\",\"name\":\"x\",\"start\":" + start + ",\"end\":" + (start + 60)
                + ",\"confidence\":" + confidence + "}";
    }

    private void awaitSet(String trackId) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(get(trackId).set()).isNotNull());
    }

    private static List<String> tuneNames(TrackDTO track) {
        return track.set().tunes().stream().map(entry -> entry.name()).toList();
    }

    private long count(String table) {
        return db.sql("SELECT count(*) FROM " + table).map(row -> row.get(0, Long.class)).one().block();
    }

    @Test
    void two_tunes_are_recognised() {
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "," + detection(KESH_SOURCE, 95) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        var stored = get(track.id());
        assertThat(tuneNames(stored)).containsExactly("Cooley's", "The Kesh");
        assertThat(stored.performances()).extracting(PerformanceDTO::startOffsetSeconds).containsExactly(0.0, 95.0);
        assertThat(states(stored)).containsOnly(IdentificationState.IDENTIFIED);
        assertThat(SIDECAR.tunesAsked.getFirst()).isEqualTo(ANY);
    }

    @Test
    void the_set_already_exists() {
        var existing = createSet(cooleys, kesh);
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "," + detection(KESH_SOURCE, 95) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(get(track.id()).set().id()).isEqualTo(existing.id());
        assertThat(count("set_sets")).isEqualTo(1);
    }

    @Test
    void another_order_is_another_set() {
        var existing = createSet(cooleys, kesh);
        SIDECAR.hears("[" + detection(KESH_SOURCE, 0) + "," + detection(COOLEYS_SOURCE, 95) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(get(track.id()).set().id()).isNotEqualTo(existing.id());
        assertThat(tuneNames(get(track.id()))).containsExactly("The Kesh", "Cooley's");
    }

    @Test
    void one_tune() {
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(tuneNames(get(track.id()))).containsExactly("Cooley's");
        assertThat(states(get(track.id()))).containsExactly(IdentificationState.IDENTIFIED);
    }

    @Test
    void weak_detections_are_ignored() {
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0, 0.8) + "," + detection(KESH_SOURCE, 95, 0.2) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(tuneNames(get(track.id()))).containsExactly("Cooley's");
    }

    @Test
    void a_split_detection_is_one_entry() {
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "," + detection(COOLEYS_SOURCE, 20) + "," + detection(KESH_SOURCE, 95) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(tuneNames(get(track.id()))).containsExactly("Cooley's", "The Kesh");
    }

    @Test
    void a_repeated_tune() {
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "," + detection(KESH_SOURCE, 60) + "," + detection(COOLEYS_SOURCE, 120) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(tuneNames(get(track.id()))).containsExactly("Cooley's", "The Kesh", "Cooley's");
        assertThat(get(track.id()).performances()).hasSize(3);
    }

    @Test
    void more_than_twenty_entries() {
        var detections = new java.util.ArrayList<String>();
        for (int i = 0; i < 25; i++) {
            detections.add(detection(i % 2 == 0 ? COOLEYS_SOURCE : KESH_SOURCE, i * 10));
        }
        SIDECAR.hears("[" + String.join(",", detections) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(get(track.id()).performances()).hasSize(20);
        assertThat(get(track.id()).set().tunes()).hasSize(20);
    }

    @Test
    void nothing_heard_without_a_set() {
        var track = uploadWithoutSet();
        settle(1);

        var stored = get(track.id());
        assertThat(stored.set()).isNull();
        assertThat(stored.performances()).isEmpty();
        assertThat(stored.title()).isEqualTo("Knees Up");
    }

    @Test
    void recognised_but_unusable() {
        SIDECAR.hears("[" + detection("999999", 0) + "]");

        var track = uploadWithoutSet();
        settle(1);

        assertThat(get(track.id()).set()).isNull();
    }

    @Test
    void an_unknown_tune_is_imported() {
        SIDECAR.hears("[" + detection("27", 0) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(count("tune_tunes WHERE source_id = 27")).isEqualTo(1);
        assertThat(tuneNames(get(track.id()))).containsExactly("Cooley's");
    }

    @Test
    void a_known_tune_is_not_imported_again() {
        db.sql("UPDATE tune_tunes SET source_id = 27 WHERE id = :id").bind("id", cooleys).then().block();
        SIDECAR.hears("[" + detection("27", 0) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(get(track.id()).set().tunes().getFirst().id()).isEqualTo(cooleys);
        assertThat(count("tune_tunes")).isEqualTo(2);
    }

    @Test
    void one_tune_cannot_be_imported() {
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "," + detection("500", 40) + "," + detection(KESH_SOURCE, 95) + "]");

        var track = uploadWithoutSet();
        awaitSet(track.id());

        assertThat(tuneNames(get(track.id()))).containsExactly("Cooley's", "The Kesh");
        assertThat(get(track.id()).performances()).extracting(PerformanceDTO::startOffsetSeconds).containsExactly(0.0, 95.0);
    }

    @Test
    void a_set_given_while_the_analysis_runs_wins() {
        SIDECAR.delayMillis = 2_000;
        SIDECAR.hears("[" + detection(KESH_SOURCE, 0) + "]");
        var track = uploadWithoutSet();
        var chosen = createSet(cooleys);
        trackService.execute(new AttachSet(track.id(), chosen.id(), List.of(7.0))).block();

        settle(1);

        var stored = get(track.id());
        assertThat(stored.set().id()).isEqualTo(chosen.id());
        assertThat(stored.performances()).extracting(PerformanceDTO::startOffsetSeconds).containsExactly(7.0);
    }

    @Test
    void a_set_is_given_once() {
        var first = createSet(cooleys);
        var second = createSet(kesh);
        var track = uploadWithoutSet();
        settle(1);

        assertThat(trackService.execute(new AttachSet(track.id(), first.id(), List.of(1.0))).block()).isNotNull();
        assertThat(trackService.execute(new AttachSet(track.id(), second.id(), List.of(2.0))).block()).isNull();

        assertThat(get(track.id()).set().id()).isEqualTo(first.id());
    }

    @Test
    void service_down_without_a_set() {
        SIDECAR.status = 503;
        SIDECAR.hears("{\"error\":\"down\"}");

        var track = uploadWithoutSet();
        settle(1);

        assertThat(get(track.id()).set()).isNull();
        SIDECAR.status = 200;
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "]");
        rerun(track.id(), UPLOADER_TOKEN).expectStatus().isAccepted();
        awaitSet(track.id());
    }

    @Test
    void owner_reruns_a_track_without_a_set() {
        var track = uploadWithoutSet();
        settle(1);
        assertThat(get(track.id()).set()).isNull();
        SIDECAR.hears("[" + detection(COOLEYS_SOURCE, 0) + "]");

        rerun(track.id(), UPLOADER_TOKEN).expectStatus().isAccepted();
        awaitSet(track.id());

        assertThat(tuneNames(get(track.id()))).containsExactly("Cooley's");
    }

    @Test
    void a_track_with_a_set_is_analysed_as_before() {
        SIDECAR.hears("[" + detection(KESH_SOURCE, 95.5) + "]");

        var track = uploadSet();
        settle(1);

        assertThat(SIDECAR.tunesAsked.getFirst()).isNotEqualTo(ANY);
        assertThat(get(track.id()).set().id()).isEqualTo(track.set().id());
    }
}
