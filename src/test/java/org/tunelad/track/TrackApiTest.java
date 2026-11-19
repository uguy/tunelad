package org.tunelad.track;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.tunelad.TestContainerDefinitions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for openspec/specs/track: one test per spec scenario, named after it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainerDefinitions.class)
class TrackApiTest {

    static final String KNEES_UP_MP3 = "mp3/Knees_Up.mp3";
    static final String MOSSY_BANKS_MP3 = "mp3/Stephen_Doherty_Patrick_Doocey-Mossy_banks_Ravens_rock.mp3";

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void clearLibrary() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                // play responses carry whole mp3 fixtures (~3 MB), above the 256 KB default
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        client.delete().uri("/api/tracks").exchange().expectStatus().is2xxSuccessful();
    }

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
        var noTitle = new java.util.HashMap<String, String>();
        noTitle.put("description", "A reel and a song");
        upload(noTitle, KNEES_UP_MP3).expectStatus().isBadRequest();

        var noDescription = new java.util.HashMap<String, String>();
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
                        org.assertj.core.groups.Tuple.tuple("Knees Up", "A reel and a song", TrackFormat.MP3),
                        org.assertj.core.groups.Tuple.tuple("Mossy Banks", "Two reels", TrackFormat.MP3));
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

    // --- helpers -----------------------------------------------------------------------------

    /** Uploads a track; a null value omits that part, and a null file omits the file part. */
    WebTestClient.ResponseSpec upload(Map<String, String> parts, String fixture) {
        var builder = new MultipartBodyBuilder();
        parts.forEach((name, value) -> {
            if (value != null) builder.part(name, value);
        });
        if (fixture != null) builder.part("file", new ClassPathResource(fixture));
        return client.post().uri("/api/tracks")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    void uploadOk(String title, String description, String fixture) {
        upload(Map.of("title", title, "description", description), fixture)
                .expectStatus().is2xxSuccessful();
    }

    WebTestClient.ResponseSpec play(String id) {
        return client.get().uri("/api/tracks/{id}/play", id)
                .header("Range", "bytes=0-")
                .exchange();
    }

    List<TrackDTO> listTracks() {
        return client.get().uri("/api/tracks").exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
    }

    TrackDTO trackTitled(String title) {
        return listTracks().stream()
                .filter(t -> title.equals(t.title()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No track titled " + title));
    }
}
