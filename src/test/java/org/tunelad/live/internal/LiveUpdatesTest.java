package org.tunelad.live.internal;

import org.junit.jupiter.api.Test;
import org.tunelad.track.TrackDTO;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** What a client sees on the stream when tracks change, through the whole path: module events, NATS, SSE. */
class LiveUpdatesTest extends AbstractLiveTest {

    private static String body(String type, String id) {
        return "{\"type\":\"" + type + "\",\"id\":\"" + id + "\"}";
    }

    private List<String> searchTitles(String query) {
        return client.get().uri("/api/tracks/search?q={q}", query).exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody().stream().map(TrackDTO::title).toList();
    }

    @Test
    void an_upload_is_announced_once_a_search_finds_the_track() {
        var stream = follow(null);

        var id = uploadOk("Rainfall", "Rain on tin", KNEES_UP_MP3).id();

        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("track.indexed").contains(body("track.indexed", id)));
        // Told only once the copy is written: a search made on receiving it includes the track
        assertThat(searchTitles("rain")).contains("Rainfall");
    }

    @Test
    void a_computed_waveform_is_announced_once_it_can_be_fetched() {
        var stream = follow(null);

        var id = uploadOk("Rainfall", "Rain on tin", KNEES_UP_MP3).id();

        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("waveform.computed").contains(body("waveform.computed", id)));
        client.get().uri("/api/tracks/{id}/waveform", id).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("READY");
    }

    @Test
    void prepared_audio_is_announced_once_it_can_be_played() {
        var stream = follow(null);

        var id = uploadFile(Map.of("title", "Rainfall", "description", "Rain on tin"),
                named("rain.wma", org.tunelad.media.TestAudio.wma()))
                .expectStatus().isCreated().expectBody(TrackDTO.class).returnResult().getResponseBody().id();

        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("audio.prepared").contains(body("audio.prepared", id)));
        client.get().uri("/api/tracks/{id}/audio", id).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("READY");
    }

    @Test
    void audio_browsers_play_is_not_announced() {
        var stream = follow(null);

        var id = uploadOk("Rainfall", "Rain on tin", KNEES_UP_MP3).id();

        // The waveform arrives on the same stream, once the audio has been through its listeners
        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("waveform.computed").contains(body("waveform.computed", id)));
        assertThat(stream.dataOf("audio.prepared")).isEmpty();
    }

    @Test
    void an_edit_is_announced_once_a_search_finds_the_new_title() {
        var id = uploadOk("Rainfall", "Rain on tin", KNEES_UP_MP3).id();
        await().atMost(Duration.ofSeconds(15)).until(() -> searchTitles("rain").contains("Rainfall"));
        var stream = follow(null);

        edit(id, Map.of("title", "Downpour", "description", "Rain on tin, louder"), UPLOADER_TOKEN)
                .expectStatus().isOk();

        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("track.indexed").contains(body("track.indexed", id)));
        assertThat(searchTitles("downpour")).containsExactly("Downpour");
    }

    @Test
    void a_delete_is_announced_once_a_search_no_longer_finds_the_track() {
        var id = uploadOk("Rainfall", "Rain on tin", KNEES_UP_MP3).id();
        await().atMost(Duration.ofSeconds(15)).until(() -> searchTitles("rain").contains("Rainfall"));
        var stream = follow(null);

        delete(id, UPLOADER_TOKEN).expectStatus().isNoContent();

        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("track.unindexed").contains(body("track.unindexed", id)));
        assertThat(searchTitles("rain")).isEmpty();
    }

    @Test
    void a_created_tune_and_set_are_announced_to_anonymous_and_authenticated_clients() {
        var anonymous = follow(null);
        var bob = follow(org.tunelad.TestTokens.bearer("bob", "Bob Smith"));

        var tune = client.post().uri("/api/tunes").headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "The Kesh", "danceType", "jig")).exchange().expectStatus().isCreated()
                .expectBody(org.tunelad.tune.TuneDTO.class).returnResult().getResponseBody().id();
        var set = client.post().uri("/api/sets").headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tuneIds", List.of(tune))).exchange().expectStatus().isCreated()
                .expectBody(org.tunelad.set.SetDTO.class).returnResult().getResponseBody().id();

        for (var stream : List.of(anonymous, bob)) {
            await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("tune.changed").contains(body("tune.changed", tune)));
            await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("set.changed").contains(body("set.changed", set)));
        }
    }

    @Test
    void the_performances_of_a_track_are_announced_when_it_is_added_and_deleted() {
        var stream = follow(null);

        var id = uploadOk("Rainfall", "Rain on tin", KNEES_UP_MP3).id();
        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("performance.changed").contains(body("performance.changed", id)));

        var before = stream.dataOf("performance.changed").size();
        delete(id, UPLOADER_TOKEN).expectStatus().isNoContent();
        await().atMost(Duration.ofSeconds(15)).until(() -> stream.dataOf("performance.changed").size() > before);
    }
}
