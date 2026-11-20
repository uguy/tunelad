package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.account.AccountRef;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.InstanceOfAssertFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Characterization tests for openspec/specs/track: one test per spec scenario, named after it.
 */
class TrackApiTest extends AbstractApiTest {

    // --- Requirement: Upload a track ---------------------------------------------------------

    @Test
    void upload_with_all_metadata() {
        var created = upload(Map.of(
                "title", "Knees Up",
                "description", "A reel and a song",
                "artist", "Breabach",
                "album", "Frenzy of the Meeting"), KNEES_UP_MP3)
                .expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult();
        var body = created.getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isNotBlank();
        assertThat(body.owner()).isEqualTo(owner(accountOf(UPLOADER_TOKEN)));
        assertThat(created.getResponseHeaders().getLocation()).hasToString("/api/tracks/" + body.id());
        assertThat(listTracks()).singleElement().satisfies(track -> {
            assertThat(track.title()).isEqualTo("Knees Up");
            assertThat(track.description()).isEqualTo("A reel and a song");
            assertThat(track.artist()).isEqualTo("Breabach");
            assertThat(track.album()).isEqualTo("Frenzy of the Meeting");
            assertThat(track.format()).isEqualTo(TrackFormat.MP3);
            assertThat(track).isEqualTo(body);
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

    @Test
    void created_track_can_be_fetched_from_its_location() {
        var created = upload(Map.of("title", "Knees Up", "description", "A reel and a song"), KNEES_UP_MP3)
                .expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult();

        client.get().uri(created.getResponseHeaders().getLocation().toString()).exchange()
                .expectStatus().isOk()
                .expectBody(TrackDTO.class).isEqualTo(created.getResponseBody());
    }

    @Test
    void blank_title_or_description() {
        upload(Map.of("title", "  ", "description", "A reel and a song"), KNEES_UP_MP3)
                .expectStatus().isBadRequest();
        upload(Map.of("title", "Knees Up", "description", ""), KNEES_UP_MP3)
                .expectStatus().isBadRequest();

        assertThat(listTracks()).isEmpty();
    }

    // --- Requirement: Accepted audio formats ----------------------------------------------------

    @ParameterizedTest
    @CsvSource({"mp3/Knees_Up.mp3, MP3", "audio/tune.m4a, M4A", "audio/tune.ogg, OGG", "audio/tune.flac, FLAC",
            "audio/tune.wav, WAV"})
    void upload_each_supported_format(String fixture, TrackFormat format) {
        var created = uploadOk("Tune", "One second of A", fixture);

        assertThat(created.format()).isEqualTo(format);
        assertThat(trackTitled("Tune").format()).isEqualTo(format);
    }

    @Test
    void extension_in_upper_case() throws IOException {
        var created = uploadFile(Map.of("title", "Tune", "description", "One second of A"), renamed("audio/tune.flac", "TUNE.FLAC"))
                .expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();

        assertThat(created.format()).isEqualTo(TrackFormat.FLAC);
    }

    @Test
    void unsupported_file() throws IOException {
        uploadFile(Map.of("title", "Notes", "description", "Not audio"), renamed("audio/tune.wav", "notes.txt"))
                .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        uploadFile(Map.of("title", "No extension", "description", "Not audio"), renamed("audio/tune.wav", "tune"))
                .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        assertThat(listTracks()).extracting(TrackDTO::title).doesNotContain("Notes", "No extension");
        assertThat(storedAudioFiles()).isZero();
    }

    // --- Requirement: Upload size limit ---------------------------------------------------------

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    @Test
    void file_over_the_limit() throws IOException {
        uploadFile(Map.of("title", "Too Big", "description", "One byte too many"), fileOfSize(MAX_FILE_SIZE + 1))
                .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

        assertThat(listTracks()).extracting(TrackDTO::title).doesNotContain("Too Big");
        assertThat(storedAudioFiles()).isZero();
    }

    @Test
    void file_at_the_limit() throws IOException {
        uploadFile(Map.of("title", "Just Right", "description", "Exactly 100 MB"), fileOfSize(MAX_FILE_SIZE))
                .expectStatus().isCreated();
    }

    /** A copy of a fixture under another file name, which is the name the upload's file part carries. */
    private Resource renamed(String fixture, String filename) throws IOException {
        var file = tempDir.resolve(filename);
        Files.write(file, new ClassPathResource(fixture).getContentAsByteArray());
        return new FileSystemResource(file);
    }

    /** A sparse mp3-named file of the given size, so it takes neither memory nor much disk. */
    private Resource fileOfSize(long size) throws IOException {
        var file = tempDir.resolve("big-" + size + ".mp3");
        try (var raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(size);
        }
        return new FileSystemResource(file);
    }

    private long storedAudioFiles() {
        return mongo.getCollection("fs.files").flatMap(files -> Mono.from(files.countDocuments())).block();
    }

    @TempDir
    Path tempDir;

    @Test
    void upload_is_documented_with_its_responses() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/tracks'].post.responses['201'].headers['Location']").exists()
                .jsonPath("$.paths['/api/tracks'].post.responses['201'].content['application/json'].schema['$ref']")
                .isEqualTo("#/components/schemas/TrackDTO")
                .jsonPath("$.paths['/api/tracks'].post.responses['400']").exists()
                .jsonPath("$.paths['/api/tracks'].post.responses['413']").exists()
                .jsonPath("$.paths['/api/tracks'].post.responses['415']").exists()
                .jsonPath("$.components.schemas.TrackDTO.properties.format.enum").value(formats ->
                        assertThat(formats).asInstanceOf(InstanceOfAssertFactories.LIST)
                                .contains("MP3", "M4A", "OGG", "FLAC", "WAV"));
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

        var alice = accountOf(UPLOADER_TOKEN);

        assertThat(listTracks())
                .extracting(TrackDTO::title, TrackDTO::description, TrackDTO::format, TrackDTO::owner)
                .containsExactlyInAnyOrder(
                        tuple("Knees Up", "A reel and a song", TrackFormat.MP3, owner(alice)),
                        tuple("Mossy Banks", "Two reels", TrackFormat.MP3, owner(alice)));
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

    // --- Requirement: Uploading requires authentication -----------------------------------------

    @Test
    void anonymous_upload() {
        upload(Map.of("title", "Knees Up", "description", "A reel and a song"), KNEES_UP_MP3, null)
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION);

        assertThat(listTracks()).extracting(TrackDTO::title).doesNotContain("Knees Up");
    }

    // --- Requirement: Every track has an owner -------------------------------------------------

    @Test
    void owner_is_the_uploader() {
        var alice = accountOf(UPLOADER_TOKEN);

        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        var owner = trackTitled("Knees Up").owner();
        assertThat(owner.id()).isEqualTo(alice.id());
        assertThat(owner.displayName()).isEqualTo("Alice Liddell");
    }

    @Test
    void owner_on_a_single_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        var track = client.get().uri("/api/tracks/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();

        assertThat(track.owner()).isEqualTo(owner(accountOf(UPLOADER_TOKEN)));
    }

    @Test
    void owners_of_different_uploaders() {
        var bobToken = TestTokens.bearer("bob", "Bob Smith");
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        upload(Map.of("title", "Mossy Banks", "description", "Two reels"), MOSSY_BANKS_MP3, bobToken)
                .expectStatus().isCreated();

        assertThat(trackTitled("Knees Up").owner()).isEqualTo(owner(accountOf(UPLOADER_TOKEN)));
        assertThat(trackTitled("Mossy Banks").owner()).isEqualTo(owner(accountOf(bobToken)));
    }

    // --- Requirement: Browse tracks without logging in -----------------------------------------
    // The client sends no session or token unless a test adds one.

    @Test
    void anonymous_list() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        client.get().uri("/api/tracks").exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).value(tracks ->
                        assertThat(tracks).extracting(TrackDTO::title).containsExactly("Knees Up"));
    }

    @Test
    void anonymous_get() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        client.get().uri("/api/tracks/{id}", trackTitled("Knees Up").id()).exchange()
                .expectStatus().isOk();
    }

    // --- Requirement: Edit a track's metadata -----------------------------------------------------

    private static final String BOB_TOKEN = TestTokens.bearer("bob", "Bob Smith");

    @Test
    void owner_edits_all_metadata() {
        upload(Map.of("title", "Knees Up", "description", "A reel and a song", "artist", "Breabach"), KNEES_UP_MP3)
                .expectStatus().isCreated();
        var before = trackTitled("Knees Up");

        var updated = edit(before.id(), Map.of(
                "title", "Knees Up (live)",
                "description", "Recorded at Celtic Connections",
                "artist", "Breabach",
                "album", "Live"), UPLOADER_TOKEN)
                .expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();

        var expected = new TrackDTO(before.id(), "Knees Up (live)", "Breabach", "Live",
                "Recorded at Celtic Connections", before.format(), before.owner());
        assertThat(updated).isEqualTo(expected);
        assertThat(getTrack(before.id())).isEqualTo(expected);
    }

    @Test
    void optional_metadata_omitted() {
        upload(Map.of("title", "Knees Up", "description", "A reel and a song",
                "artist", "Breabach", "album", "Frenzy of the Meeting"), KNEES_UP_MP3)
                .expectStatus().isCreated();
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Knees Up", "description", "A reel and a song"), UPLOADER_TOKEN)
                .expectStatus().isOk();

        var track = getTrack(id);
        assertThat(track.artist()).isNull();
        assertThat(track.album()).isNull();
    }

    @Test
    void audio_unchanged_after_an_edit() throws Exception {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Knees Up (live)", "description", "Live"), UPLOADER_TOKEN).expectStatus().isOk();

        var body = play(id, null).expectStatus().isOk()
                .expectBody(byte[].class).returnResult().getResponseBody();
        assertThat(body).isEqualTo(new ClassPathResource(KNEES_UP_MP3).getContentAsByteArray());
    }

    @Test
    void required_field_missing_or_blank() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("description", "A reel and a song"), UPLOADER_TOKEN).expectStatus().isBadRequest();
        edit(id, Map.of("title", "  ", "description", "A reel and a song"), UPLOADER_TOKEN).expectStatus().isBadRequest();
        edit(id, Map.of("title", "Knees Up (live)"), UPLOADER_TOKEN).expectStatus().isBadRequest();
        edit(id, Map.of("title", "Knees Up (live)", "description", ""), UPLOADER_TOKEN).expectStatus().isBadRequest();

        assertThat(getTrack(id).title()).isEqualTo("Knees Up");
    }

    @Test
    void edit_an_unknown_track() {
        edit("no-such-track", Map.of("title", "Knees Up", "description", "A reel and a song"), UPLOADER_TOKEN)
                .expectStatus().isNotFound();
    }

    // --- Requirement: Delete a track -----------------------------------------------------------

    @Test
    void owner_deletes_a_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        uploadOk("Mossy Banks", "Two reels", MOSSY_BANKS_MP3);
        var id = trackTitled("Knees Up").id();

        delete(id, UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(listTracks()).extracting(TrackDTO::title).containsExactly("Mossy Banks");
        client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void deleted_track_can_no_longer_be_played() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        delete(id, UPLOADER_TOKEN).expectStatus().isNoContent();

        play(id).expectStatus().isNotFound();
    }

    @Test
    void delete_an_unknown_track() {
        delete("no-such-track", UPLOADER_TOKEN).expectStatus().isNotFound();
    }

    // --- Requirement: Only the owner or an administrator changes a track -----------------------

    @Test
    void another_user_edits_a_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Bob's now", "description", "Mine"), BOB_TOKEN).expectStatus().isForbidden();

        assertThat(getTrack(id).title()).isEqualTo("Knees Up");
    }

    @Test
    void another_user_deletes_a_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        delete(id, BOB_TOKEN).expectStatus().isForbidden();

        assertThat(listTracks()).extracting(TrackDTO::id).containsExactly(id);
        // Read the body: an unread response stays in flight and stalls the server's graceful shutdown
        play(id).expectStatus().is2xxSuccessful()
                .expectBody(byte[].class).value(body -> assertThat(body).isNotEmpty());
    }

    @Test
    void administrator_edits_someone_elses_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Knees Up (edited)", "description", "Tidied up"), TestTokens.admin())
                .expectStatus().isOk();

        var track = getTrack(id);
        assertThat(track.title()).isEqualTo("Knees Up (edited)");
        assertThat(track.owner()).isEqualTo(owner(accountOf(UPLOADER_TOKEN)));
    }

    @Test
    void administrator_deletes_someone_elses_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        delete(id, TestTokens.admin()).expectStatus().isNoContent();

        assertThat(listTracks()).isEmpty();
    }

    @Test
    void unknown_track_is_not_found_for_another_user_too() {
        edit("no-such-track", Map.of("title", "Knees Up", "description", "x"), BOB_TOKEN).expectStatus().isNotFound();
        delete("no-such-track", BOB_TOKEN).expectStatus().isNotFound();
    }

    // --- Requirement: Changing a track requires authentication ---------------------------------

    @Test
    void anonymous_edit() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Anonymous", "description", "x"), null)
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION);

        assertThat(getTrack(id).title()).isEqualTo("Knees Up");
    }

    @Test
    void anonymous_delete() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        delete(id, null)
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION);

        assertThat(listTracks()).extracting(TrackDTO::id).containsExactly(id);
    }

    private TrackDTO getTrack(String id) {
        return client.get().uri("/api/tracks/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private static TrackOwner owner(AccountRef account) {
        return new TrackOwner(account.id(), account.displayName());
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
