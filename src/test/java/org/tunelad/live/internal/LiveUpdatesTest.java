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
}
