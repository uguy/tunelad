package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

    // --- Removed requirement: Delete all tracks -----------------------------------------------

    @Test
    void deleting_all_tracks_is_not_allowed() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        // Authenticated with a bearer token, so that the request gets past CSRF protection to the routing
        client.delete().uri("/api/tracks")
                .headers(headers -> headers.setBearerAuth(UPLOADER_TOKEN))
                .exchange().expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        assertThat(listTracks()).extracting(TrackDTO::title).containsExactly("Knees Up");
    }
}
