package org.tunelad.live.internal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.tunelad.TestTokens;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** {@code GET /api/live}: who receives what. Events are published on NATS as another instance would. */
class LiveStreamTest extends AbstractLiveTest {

    @Autowired MeterRegistry meters;

    @Autowired org.tunelad.track.TrackService trackService;

    private static final String ALICE = UPLOADER_TOKEN;
    private static final String BOB = TestTokens.bearer("bob", "Bob Smith");

    private static String body(String type, String id) {
        return "{\"type\":\"" + type + "\",\"id\":\"" + id + "\"}";
    }

    // --- Requirement: Live update stream ---------------------------------------------------------

    @Test
    void anonymous_client_opens_the_stream() {
        var stream = follow(null);

        assertThat(stream.status).isEqualTo(HttpStatus.OK);
        assertThat(stream.contentType).isNotNull().satisfies(type -> assertThat(type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue());
    }

    @Test
    void authenticated_client_opens_the_stream() {
        var stream = follow(ALICE);

        assertThat(stream.status).isEqualTo(HttpStatus.OK);
        assertThat(stream.contentType).isNotNull().satisfies(type -> assertThat(type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue());
    }

    @Test
    void the_stream_stays_open() {
        var stream = follow(null);

        // A heartbeat arrives at once, and the connection is still there afterwards
        await().atMost(Duration.ofSeconds(5)).until(() -> stream.comments() >= 1);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> stream.status.is2xxSuccessful());
    }

    // --- Requirement: Events carry identifiers, not data -----------------------------------------

    @Test
    void shape_of_an_event() throws Exception {
        var stream = follow(null);

        publishRaw(LiveSubjects.library("waveform.computed"), body("waveform.computed", "42"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !stream.events().isEmpty());
        assertThat(stream.names()).containsExactly("waveform.computed");
        assertThat(stream.dataOf("waveform.computed")).containsExactly("{\"type\":\"waveform.computed\",\"id\":\"42\"}");
    }

    // --- Requirement: Library events reach every connection --------------------------------------

    @Test
    void anonymous_and_authenticated_clients_both_receive_a_library_event() throws Exception {
        var anonymous = follow(null);
        var alice = follow(ALICE);

        publishRaw(LiveSubjects.library("track.indexed"), body("track.indexed", "42"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !anonymous.events().isEmpty() && !alice.events().isEmpty());
        assertThat(anonymous.dataOf("track.indexed")).containsExactly(body("track.indexed", "42"));
        assertThat(alice.dataOf("track.indexed")).containsExactly(body("track.indexed", "42"));
    }

    @Test
    void giving_a_track_a_set_announces_it_as_indexed() {
        var parts = new java.util.HashMap<String, String>();
        parts.put("title", "Knees Up");
        parts.put("setId", null);
        var track = upload(parts, KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(org.tunelad.track.TrackDTO.class).returnResult().getResponseBody();
        var stream = follow(null);

        trackService.execute(new org.tunelad.track.command.AttachSet(
                track.id(), createSet(createTune("Cooley's", "reel")).id(), java.util.List.of(12.0))).block();

        await().atMost(Duration.ofSeconds(10)).until(() -> stream.dataOf("track.indexed").contains(body("track.indexed", track.id())));
    }

    @Test
    void anonymous_and_authenticated_clients_both_receive_an_audio_event() throws Exception {
        var anonymous = follow(null);
        var alice = follow(ALICE);

        publishRaw(LiveSubjects.library("audio.prepared"), body("audio.prepared", "42"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !anonymous.events().isEmpty() && !alice.events().isEmpty());
        assertThat(anonymous.dataOf("audio.prepared")).containsExactly(body("audio.prepared", "42"));
        assertThat(alice.dataOf("audio.prepared")).containsExactly(body("audio.prepared", "42"));
    }

    // --- Requirement: Account events reach only that account -------------------------------------

    @Test
    void two_tabs_of_the_same_account_both_receive_an_account_event() throws Exception {
        var aliceId = accountOf(ALICE).id();
        var firstTab = follow(ALICE);
        var secondTab = follow(ALICE);

        publishRaw(LiveSubjects.account(aliceId, "favourites.changed"), body("favourites.changed", "7"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !firstTab.events().isEmpty() && !secondTab.events().isEmpty());
        assertThat(firstTab.names()).containsExactly("favourites.changed");
        assertThat(secondTab.names()).containsExactly("favourites.changed");
    }

    @Test
    void other_accounts_and_anonymous_clients_do_not_receive_an_account_event() throws Exception {
        var aliceId = accountOf(ALICE).id();
        var alice = follow(ALICE);
        var bob = follow(BOB);
        var anonymous = follow(null);

        publishRaw(LiveSubjects.account(aliceId, "favourites.changed"), body("favourites.changed", "7"));
        // Events arrive in the order they were published: whoever has this one would have had the one before it
        publishRaw(LiveSubjects.library("track.indexed"), body("track.indexed", "marker"));

        await().atMost(Duration.ofSeconds(10)).until(() ->
                alice.names().size() == 2 && bob.names().size() == 1 && anonymous.names().size() == 1);
        assertThat(alice.names()).containsExactly("favourites.changed", "track.indexed");
        assertThat(bob.names()).containsExactly("track.indexed");
        assertThat(anonymous.names()).containsExactly("track.indexed");
    }

    // --- Requirement: The client cannot choose its audience --------------------------------------

    @Test
    void asking_for_another_accounts_events_gets_the_library_and_ones_own_only() throws Exception {
        var aliceId = accountOf(ALICE).id();
        var bobId = accountOf(BOB).id();
        var alice = follow("/api/live?account=" + bobId + "&accountKey=" + LiveSubjects.accountKey(bobId), ALICE);

        publishRaw(LiveSubjects.account(bobId, "favourites.changed"), body("favourites.changed", "bobs"));
        publishRaw(LiveSubjects.account(aliceId, "favourites.changed"), body("favourites.changed", "alices"));
        publishRaw(LiveSubjects.library("track.indexed"), body("track.indexed", "42"));

        await().atMost(Duration.ofSeconds(10)).until(() -> alice.names().size() == 2);
        assertThat(alice.dataOf("favourites.changed")).containsExactly(body("favourites.changed", "alices"));
        assertThat(alice.dataOf("track.indexed")).containsExactly(body("track.indexed", "42"));
    }

    @Test
    void headers_and_cookies_naming_an_account_do_not_choose_the_audience_either() throws Exception {
        var bobId = accountOf(BOB).id();
        var anonymous = LiveStream.open(client, null, headers -> {
            headers.add("X-Account-Id", bobId);
            headers.add("X-Account-Key", LiveSubjects.accountKey(bobId));
            headers.add(HttpHeaders.COOKIE, "account=" + LiveSubjects.accountKey(bobId));
        });

        publishRaw(LiveSubjects.account(bobId, "favourites.changed"), body("favourites.changed", "bobs"));
        publishRaw(LiveSubjects.library("track.indexed"), body("track.indexed", "marker"));

        await().atMost(Duration.ofSeconds(10)).until(() -> anonymous.names().size() == 1);
        assertThat(anonymous.names()).containsExactly("track.indexed");
        anonymous.close();
    }

    // --- Requirement: A slow connection does not hold up the others ------------------------------

    @Test
    void a_stalled_client_does_not_hold_up_another() throws Exception {
        var stalled = LiveStream.openStalled(client);
        var reading = follow(null);

        for (int i = 0; i < 300; i++) {
            nats().publish(LiveSubjects.library("track.indexed"), body("track.indexed", "id-" + i).getBytes());
        }
        nats().flush(Duration.ofSeconds(5));

        await().atMost(Duration.ofSeconds(10)).until(() -> reading.dataOf("track.indexed").size() == 300);
        stalled.close();
    }

    // --- The endpoint is documented ---------------------------------------------------------------

    @Test
    void the_stream_is_documented() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/live'].get.tags[0]").isEqualTo("Live")
                .jsonPath("$.paths['/api/live'].get.responses['200'].content['text/event-stream']").exists();
    }

    // --- Metrics ---------------------------------------------------------------------------------

    @Test
    void open_connections_are_gauged_and_events_delivered_are_counted() throws Exception {
        var aliceId = accountOf(ALICE).id();
        var before = meters.counter("tunelad.live.events.delivered", "type", "playlist.changed").count();
        var stream = follow(ALICE);
        await().atMost(Duration.ofSeconds(5)).until(() -> meters.get("tunelad.live.connections").gauge().value() >= 1);

        publishRaw(LiveSubjects.account(aliceId, "playlist.changed"), body("playlist.changed", "p1"));

        await().atMost(Duration.ofSeconds(10)).until(() -> stream.names().contains("playlist.changed"));
        assertThat(meters.counter("tunelad.live.events.delivered", "type", "playlist.changed").count())
                .isEqualTo(before + 1);

        stream.close();
        await().atMost(Duration.ofSeconds(10)).until(() -> meters.get("tunelad.live.connections").gauge().value() == 0);
    }

    @Test
    void an_unknown_type_is_counted_as_other_so_the_series_stay_bounded() throws Exception {
        var stream = follow(null);

        publishRaw(LiveSubjects.library("made.up.type"), body("made.up.type", "1"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !stream.events().isEmpty());
        assertThat(meters.find("tunelad.live.events.delivered").tag("type", "made.up.type").counter()).isNull();
        assertThat(meters.counter("tunelad.live.events.delivered", "type", "other").count()).isPositive();
    }
}
