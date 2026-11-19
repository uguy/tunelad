package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.tunelad.AbstractApiTest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Characterization tests for openspec/specs/track: one test per spec scenario, named after it.
 */
class TrackApiTest extends AbstractApiTest {

    // --- Requirement: Upload a track ---------------------------------------------------------

    @Test
    void upload_with_all_metadata() {
        upload(Map.of(
                "title", "Knees Up",
                "description", "A reel and a song",
                "artist", "Breabach",
                "album", "Frenzy of the Meeting"), KNEES_UP_MP3)
                .expectStatus().is2xxSuccessful();

        assertThat(listTracks()).singleElement().satisfies(track -> {
            assertThat(track.title()).isEqualTo("Knees Up");
            assertThat(track.description()).isEqualTo("A reel and a song");
            assertThat(track.artist()).isEqualTo("Breabach");
            assertThat(track.album()).isEqualTo("Frenzy of the Meeting");
            assertThat(track.format()).isEqualTo(TrackFormat.MP3);
        });
    }

    @Test
    void upload_without_optional_metadata() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        assertThat(listTracks()).singleElement().satisfies(track -> {
            assertThat(track.title()).isEqualTo("Knees Up");
            assertThat(track.description()).isEqualTo("A reel and a song");
            assertThat(track.artist()).isNull();
            assertThat(track.album()).isNull();
        });
    }

    @Test
    void required_part_missing() {
        var noTitle = new HashMap<String, String>();
        noTitle.put("description", "A reel and a song");
        upload(noTitle, KNEES_UP_MP3).expectStatus().isBadRequest();

        var noDescription = new HashMap<String, String>();
        noDescription.put("title", "Knees Up");
        upload(noDescription, KNEES_UP_MP3).expectStatus().isBadRequest();

        upload(Map.of("title", "Knees Up", "description", "A reel and a song"), null)
                .expectStatus().isBadRequest();

        assertThat(listTracks()).isEmpty();
    }

    // --- Requirement: Assign a unique identifier to each track --------------------------------

    @Test
    void two_uploads_with_the_same_title() {
        uploadOk("Knees Up", "first", KNEES_UP_MP3);
        uploadOk("Knees Up", "second", KNEES_UP_MP3);

        assertThat(listTracks())
                .hasSize(2)
                .extracting(TrackDTO::id)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    }

    // --- Requirement: List tracks -------------------------------------------------------------

    @Test
    void library_with_tracks() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        uploadOk("Mossy Banks", "Two reels", MOSSY_BANKS_MP3);

        assertThat(listTracks())
                .extracting(TrackDTO::title, TrackDTO::description, TrackDTO::format)
                .containsExactlyInAnyOrder(
                        tuple("Knees Up", "A reel and a song", TrackFormat.MP3),
                        tuple("Mossy Banks", "Two reels", TrackFormat.MP3));
    }

    @Test
    void empty_library() {
        client.get().uri("/api/tracks").exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    // --- Requirement: Get a track by id -------------------------------------------------------

    @Test
    void existing_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var listed = trackTitled("Knees Up");

        client.get().uri("/api/tracks/{id}", listed.id()).exchange()
                .expectStatus().isOk()
                .expectBody(TrackDTO.class).isEqualTo(listed);
    }

    @Test
    void unknown_id() {
        client.get().uri("/api/tracks/{id}", "no-such-track").exchange()
                .expectStatus().isNotFound();
    }

    // --- Requirement: Play a track ------------------------------------------------------------
    // Requests send "Range: bytes=0-" like a browser audio element (see design.md).

    @Test
    void play_an_uploaded_track() throws Exception {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        var body = play(id)
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentTypeCompatibleWith("audio/mpeg")
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(new ClassPathResource(KNEES_UP_MP3).getContentAsByteArray());
    }

    @Test
    void play_without_a_range_header() throws Exception {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();
        var file = new ClassPathResource(KNEES_UP_MP3).getContentAsByteArray();

        var body = play(id, null)
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("audio/mpeg")
                .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .expectHeader().contentLength(file.length)
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(file);
    }

    @Test
    void play_an_unknown_track() {
        play("no-such-track").expectStatus().isNotFound();
    }

    // --- Requirement: Delete all tracks -------------------------------------------------------

    @Test
    void clear_a_populated_library() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        uploadOk("Mossy Banks", "Two reels", MOSSY_BANKS_MP3);
        var ids = listTracks().stream().map(TrackDTO::id).toList();

        client.delete().uri("/api/tracks").exchange().expectStatus().is2xxSuccessful();

        assertThat(listTracks()).isEmpty();
        for (var id : ids) {
            client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isNotFound();
            play(id).expectStatus().isNotFound();
        }
    }
}
