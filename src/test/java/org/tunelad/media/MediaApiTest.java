package org.tunelad.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.HttpStatus;
import org.tunelad.AbstractApiTest;
import org.tunelad.track.TrackDTO;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
                .expectHeader().contentTypeCompatibleWith("audio/mpeg")
                // Read the body: an unread response stays in flight and stalls the server's graceful shutdown
                .expectBody(byte[].class).value(body -> assertThat(body).isNotEmpty());
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

    @ParameterizedTest
    @CsvSource({"audio/tune.m4a, audio/mp4", "audio/tune.ogg, audio/ogg", "audio/tune.flac, audio/flac",
            "audio/tune.wav, audio/wav"})
    void play_each_supported_format(String fixture, String contentType) throws Exception {
        var file = new ClassPathResource(fixture).getContentAsByteArray();
        var id = uploadOk("Tune", "One second of A", fixture).id();

        var whole = play(id, null)
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(contentType)
                .expectBody(byte[].class).returnResult().getResponseBody();
        var range = play(id, "bytes=0-")
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().contentTypeCompatibleWith(contentType)
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(whole).isEqualTo(file);
        assertThat(range).isEqualTo(file);
    }

    @Test
    void audio_stored_before_formats_were_recorded() {
        var id = uploadKneesUp();
        // Audio stored before formats were recorded has no content type
        db.sql("UPDATE media_files SET content_type = '' WHERE file_key = :key").bind("key", id).then().block();

        play(id).expectStatus().is2xxSuccessful()
                .expectHeader().contentTypeCompatibleWith("audio/mpeg")
                // Read the body: an unread response stays in flight and stalls the server's graceful shutdown
                .expectBody(byte[].class).value(body -> assertThat(body).isNotEmpty());
    }

    @Test
    void play_an_unknown_track() {
        play("no-such-track").expectStatus().isNotFound();
    }

    // --- Requirement: Serve a byte range of a track's audio -----------------------------------
    // Knees_Up.mp3 is 3,315,251 bytes, stored as chunks of 261,120 bytes.

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

    @Test
    void waveform_is_documented() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/tracks/{id}/waveform'].get.tags[0]").isEqualTo("Media")
                .jsonPath("$.paths['/api/tracks/{id}/waveform'].get.responses['200'].content['application/json'].schema['$ref']")
                .isEqualTo("#/components/schemas/Waveform")
                .jsonPath("$.paths['/api/tracks/{id}/waveform'].get.responses['404']").exists()
                .jsonPath("$.components.schemas.Waveform.properties.status.enum").isEqualTo(List.of("PENDING", "READY", "FAILED"))
                .jsonPath("$.components.schemas.Waveform.properties.peaks.maxItems").isEqualTo(200)
                .jsonPath("$.components.schemas.Waveform.required").isEqualTo(List.of("peaks", "status"));
    }

    private String uploadKneesUp() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        return trackTitled("Knees Up").id();
    }

    private static byte[] kneesUpBytes() throws Exception {
        return new ClassPathResource(KNEES_UP_MP3).getContentAsByteArray();
    }

    // --- Requirement: Get the waveform of a track ------------------------------------------------

    @Test
    void waveform_of_a_processed_track() {
        var id = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3).id();
        awaitWaveform(id, "READY");

        var waveform = getWaveform(id)
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectHeader().cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .expectBody(WaveformBody.class).returnResult().getResponseBody();

        assertThat(waveform.status()).isEqualTo("READY");
        assertThat(waveform.peaks()).hasSize(200).allMatch(peak -> peak >= 0 && peak <= 1);
        assertThat(waveform.peaks()).contains(1.0);
    }

    @Test
    void peaks_follow_the_loudness_of_the_audio() {
        var id = uploadFile(Map.of("title", "Steps", "description", "Silence, then a tone"),
                named(TestAudio.silenceThenTone(), "steps.wav")).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody().id();

        var peaks = awaitWaveform(id, "READY").peaks();

        assertThat(peaks.subList(0, 90)).allMatch(peak -> peak < 0.05);
        assertThat(peaks.subList(110, 200)).allMatch(peak -> peak > 0.5);
    }

    @Test
    void waveform_not_computed_yet() {
        var id = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3).id();
        awaitWaveform(id, "READY");
        // As if still being computed
        db.sql("UPDATE media_waveforms SET status = 'PENDING', peaks = '{}' WHERE file_key = :key")
                .bind("key", id).then().block();

        var waveform = getWaveform(id)
                .expectStatus().isOk()
                .expectBody(WaveformBody.class).returnResult();

        assertThat(waveform.getResponseBody()).isEqualTo(new WaveformBody("PENDING", List.of()));
        assertThat(waveform.getResponseHeaders().getCacheControl()).doesNotContain("max-age=3600");
    }

    @Test
    void waveform_of_an_unknown_track() {
        getWaveform("no-such-track").expectStatus().isNotFound();
    }

    // --- Requirement: Compute a track's waveform after upload ------------------------------------

    @Test
    void waveform_ready_shortly_after_upload() {
        var id = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3).id();

        assertThat(awaitWaveform(id, "READY").peaks()).hasSize(200);
    }

    @ParameterizedTest
    @CsvSource({"audio/tune.m4a", "audio/tune.ogg", "audio/tune.flac", "audio/tune.wav"})
    void every_supported_format_gets_a_waveform(String fixture) {
        var id = uploadOk("Tune", "One second of A", fixture).id();

        assertThat(awaitWaveform(id, "READY").peaks()).hasSize(200);
    }

    @Test
    void undecodable_audio() {
        var noise = TestAudio.noise();
        var id = uploadFile(Map.of("title", "Noise", "description", "Not audio at all"), named(noise, "noise.mp3"))
                .expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody().id();

        assertThat(awaitWaveform(id, "FAILED").peaks()).isEmpty();
        assertThat(play(id, null).expectStatus().isOk().expectBody(byte[].class).returnResult().getResponseBody())
                .isEqualTo(noise);
    }

    // --- Requirement: Delete a track's waveform with its audio -----------------------------------

    @Test
    void waveform_gone_after_deleting_the_track() {
        var id = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3).id();
        awaitWaveform(id, "READY");

        delete(id, UPLOADER_TOKEN).expectStatus().isNoContent();

        getWaveform(id).expectStatus().isNotFound();
    }

    // --- Requirement: Get a waveform without logging in ------------------------------------------

    @Test
    void anonymous_waveform() {
        var id = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3).id();

        // getWaveform() sends no session or token
        assertThat(awaitWaveform(id, "READY").status()).isEqualTo("READY");
        getWaveform(id).expectStatus().isOk();
    }

    /** The waveform as a client reads it. */
    record WaveformBody(String status, List<Double> peaks) {
    }

    private WebTestClient.ResponseSpec getWaveform(String id) {
        return client.get().uri("/api/tracks/{id}/waveform", id).exchange();
    }

    /** Polls the waveform until it has the status, for up to 10 seconds, and returns it. */
    private WaveformBody awaitWaveform(String id, String status) {
        return await().atMost(Duration.ofSeconds(10)).until(
                () -> getWaveform(id).expectStatus().isOk()
                        .expectBody(WaveformBody.class).returnResult().getResponseBody(),
                waveform -> status.equals(waveform.status()));
    }

    /** A file part with the given content and file name. */
    private static ByteArrayResource named(byte[] content, String filename) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
