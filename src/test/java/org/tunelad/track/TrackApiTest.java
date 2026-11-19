package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.tunelad.AbstractApiTest;

import java.util.Arrays;
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

    // --- Requirement: Serve a byte range of a track's audio -----------------------------------
    // Knees_Up.mp3 is 3,315,251 bytes, stored as GridFS chunks of 261,120 bytes.

    @Test
    void range_in_the_middle_of_the_file() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();

        var body = play(id, "bytes=1000000-1499999")
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().contentTypeCompatibleWith("audio/mpeg")
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 1000000-1499999/" + file.length)
                .expectHeader().contentLength(500000)
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(Arrays.copyOfRange(file, 1000000, 1500000));
    }

    @Test
    void open_ended_range() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();
        int start = 3000000;

        var body = play(id, "bytes=" + start + "-")
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE,
                        "bytes %d-%d/%d".formatted(start, file.length - 1, file.length))
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(Arrays.copyOfRange(file, start, file.length));
    }

    @Test
    void range_from_the_start_as_sent_by_a_browser() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();

        var body = play(id, "bytes=0-")
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE,
                        "bytes 0-%d/%d".formatted(file.length - 1, file.length))
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(file);
    }

    @Test
    void suffix_range() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();

        var body = play(id, "bytes=-1000")
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE,
                        "bytes %d-%d/%d".formatted(file.length - 1000, file.length - 1, file.length))
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(Arrays.copyOfRange(file, file.length - 1000, file.length));
    }

    @Test
    void range_ending_past_the_end_of_the_file() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();
        int start = file.length - 10;

        var body = play(id, "bytes=%d-%d".formatted(start, file.length + 5000))
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE,
                        "bytes %d-%d/%d".formatted(start, file.length - 1, file.length))
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(Arrays.copyOfRange(file, start, file.length));
    }

    // --- Requirement: Reject an unsatisfiable byte range --------------------------------------

    @Test
    void range_starting_past_the_end_of_the_file() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();

        play(id, "bytes=" + file.length + "-")
                .expectStatus().isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */" + file.length)
                .expectBody().isEmpty();
    }

    @Test
    void unsatisfiable_range_on_an_unknown_track() {
        play("no-such-track", "bytes=0-").expectStatus().isNotFound();
    }

    // --- Requirement: Ignore unsupported Range headers ----------------------------------------

    @Test
    void several_ranges() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();

        var body = play(id, "bytes=0-99,200-299")
                .expectStatus().isOk()
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(file);
    }

    @Test
    void malformed_range() throws Exception {
        var file = kneesUpBytes();
        var id = uploadKneesUp();

        var body = play(id, "bytes=abc")
                .expectStatus().isOk()
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).isEqualTo(file);
    }

    // --- OpenAPI description of play ----------------------------------------------------------

    @Test
    void play_is_documented_with_range_responses() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/tracks/{id}/play'].get.parameters[?(@.name == 'Range')].required").isEqualTo(false)
                .jsonPath("$.paths['/api/tracks/{id}/play'].get.responses['206'].headers['Content-Range']").exists()
                .jsonPath("$.paths['/api/tracks/{id}/play'].get.responses['416'].headers['Content-Range']").exists();
    }

    private String uploadKneesUp() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        return trackTitled("Knees Up").id();
    }

    private static byte[] kneesUpBytes() throws Exception {
        return new ClassPathResource(KNEES_UP_MP3).getContentAsByteArray();
    }

    // --- Removed requirement: Delete all tracks -----------------------------------------------

    @Test
    void deleting_all_tracks_is_not_allowed() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        client.delete().uri("/api/tracks").exchange().expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        assertThat(listTracks()).extracting(TrackDTO::title).containsExactly("Knees Up");
    }
}
