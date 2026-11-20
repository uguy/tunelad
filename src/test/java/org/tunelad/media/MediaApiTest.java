package org.tunelad.media;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.tunelad.AbstractApiTest;

import reactor.core.publisher.Mono;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for openspec/specs/media (its REST requirements): one test per spec scenario, named after it.
 * Tracks are uploaded through the track API, as a client would.
 */
class MediaApiTest extends AbstractApiTest {

    // --- Requirement: Play without logging in --------------------------------------------------

    @Test
    void anonymous_playback() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        // play() sends no session or token
        play(id).expectStatus().is2xxSuccessful()
                .expectHeader().contentTypeCompatibleWith("audio/mpeg");
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
    void audio_stored_before_formats_were_recorded() {
        var id = uploadKneesUp();
        // Audio stored before this change has no content type in its GridFS metadata
        mongo.getCollection("fs.files")
                .flatMap(files -> Mono.from(files.updateMany(new Document("filename", id),
                        new Document("$unset", new Document("metadata._contentType", "")))))
                .block();

        play(id).expectStatus().is2xxSuccessful()
                .expectHeader().contentTypeCompatibleWith("audio/mpeg");
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
                .jsonPath("$.paths['/api/tracks/{id}/play'].get.responses['416'].headers['Content-Range']").exists()
                .jsonPath("$.paths['/api/tracks/{id}/play'].get.tags[0]").isEqualTo("Media");
    }

    private String uploadKneesUp() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        return trackTitled("Knees Up").id();
    }

    private static byte[] kneesUpBytes() throws Exception {
        return new ClassPathResource(KNEES_UP_MP3).getContentAsByteArray();
    }
}
