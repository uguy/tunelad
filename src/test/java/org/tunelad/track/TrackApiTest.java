package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.account.AccountRef;

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
                .expectStatus().is2xxSuccessful();

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
