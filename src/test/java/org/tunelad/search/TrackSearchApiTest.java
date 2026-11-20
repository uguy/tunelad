package org.tunelad.search;

import org.junit.jupiter.api.Test;
import org.tunelad.AbstractApiTest;
import org.tunelad.track.TrackDTO;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for openspec/specs/search: one test per spec scenario, named after it.
 * <p>
 * Search is served from the search module's read model, which catches up with uploads shortly after they
 * complete. Uploads made to set up a scenario therefore wait until the track is searchable.
 */
class TrackSearchApiTest extends AbstractApiTest {

    // --- Requirement: Search tracks by words in title or description --------------------------

    @Test
    void match_on_a_title_word() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search("knees"))).containsExactly("Knees Up");
    }

    @Test
    void match_on_a_description_word() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search("breabach"))).containsExactly("Knees Up");
    }

    @Test
    void case_insensitive_match() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search("KNEES"))).containsExactly("Knees Up");
    }

    @Test
    void any_query_word_matches() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search("knees mossy"))).containsExactlyInAnyOrder("Knees Up", "Mossy Banks");
    }

    // --- Requirement: Match whole words, including word variants ------------------------------

    @Test
    void word_variant_matches() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search("knee"))).containsExactly("Knees Up");
    }

    @Test
    void partial_word_does_not_match() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search("kne"))).doesNotContain("Knees Up");
    }

    // --- Requirement: Order search results by relevance ---------------------------------------

    @Test
    void better_match_first() {
        // Uploaded first, so insertion order alone would put it first.
        uploadSearchable("Mossy Banks", "Two reels played on the flute", MOSSY_BANKS_MP3);
        uploadSearchable("Fiddle Jig", "A lively fiddle jig for the fiddle", KNEES_UP_MP3);

        // "Fiddle Jig" matches "fiddle" and "jig" in its title and again in its description;
        // "Mossy Banks" matches only "flute", once, in its description.
        assertThat(titles(search("fiddle jig flute"))).containsExactly("Fiddle Jig", "Mossy Banks");
    }

    // --- Requirement: Blank query returns every track -----------------------------------------

    @Test
    void empty_query() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search(""))).containsExactlyInAnyOrder("Knees Up", "Mossy Banks");
    }

    @Test
    void whitespace_only_query() {
        uploadKneesUpAndMossyBanks();

        assertThat(titles(search("   "))).containsExactlyInAnyOrder("Knees Up", "Mossy Banks");
    }

    // --- Requirement: No match returns an empty result ----------------------------------------

    @Test
    void unmatched_word() {
        uploadKneesUpAndMossyBanks();

        client.get().uri(b -> b.path("/api/tracks/search").queryParam("q", "{q}").build("bagpipes"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    // --- Requirement: Uploaded tracks become searchable shortly after upload --------------------

    @Test
    void new_track_found_after_a_short_delay() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        await().atMost(SEARCHABLE_WITHIN).untilAsserted(() ->
                assertThat(titles(search("knees"))).contains("Knees Up"));
    }

    @Test
    void searchable_track_has_the_track_list_fields() {
        upload(Map.of("title", "Knees Up", "description", "A reel and a song",
                "artist", "Breabach", "album", "Frenzy of the Meeting"), KNEES_UP_MP3)
                .expectStatus().isCreated();

        await().atMost(SEARCHABLE_WITHIN).until(() -> !search("knees").isEmpty());
        var listed = trackTitled("Knees Up");
        assertThat(listed.owner()).isNotNull();
        // Records compare every field: id, title, description, artist, album, format and owner
        assertThat(search("knees")).containsExactly(listed);
    }

    // --- Requirement: Edits and deletions reach search shortly after ----------------------------

    @Test
    void edited_track_found_by_its_new_title() {
        uploadSearchable("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Hornpipe Set", "description", "A reel and a song"), UPLOADER_TOKEN)
                .expectStatus().isOk();

        await().atMost(SEARCHABLE_WITHIN).untilAsserted(() -> {
            assertThat(search("hornpipe")).extracting(TrackDTO::id, TrackDTO::title)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(id, "Hornpipe Set"));
            assertThat(search("knees")).isEmpty();
        });
    }

    @Test
    void edited_track_has_the_track_list_fields() {
        uploadSearchable("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Hornpipe Set", "description", "Two hornpipes", "artist", "Breabach",
                "album", "Frenzy of the Meeting"), UPLOADER_TOKEN)
                .expectStatus().isOk();

        await().atMost(SEARCHABLE_WITHIN).until(() -> !search("hornpipe").isEmpty());
        // Records compare every field: id, title, description, artist, album, format and owner
        assertThat(search("hornpipe")).containsExactly(trackTitled("Hornpipe Set"));
    }

    @Test
    void deleted_track_disappears_from_search() {
        uploadKneesUpAndMossyBanks();

        delete(trackTitled("Knees Up").id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        await().atMost(SEARCHABLE_WITHIN).untilAsserted(() ->
                assertThat(titles(search(""))).containsExactly("Mossy Banks"));
    }

    // --- Requirement: Search without logging in ------------------------------------------------

    @Test
    void anonymous_search() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        await().atMost(SEARCHABLE_WITHIN).until(() -> !search("knees").isEmpty());

        // No session or token
        client.get().uri("/api/tracks/search?q={q}", "knees").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("Knees Up"));
    }

    // --- helpers -----------------------------------------------------------------------------

    /** How soon an uploaded track must be searchable, per the spec. */
    private static final Duration SEARCHABLE_WITHIN = Duration.ofSeconds(5);

    /** Uploads a track and waits until search returns it. */
    private void uploadSearchable(String title, String description, String fixture) {
        uploadOk(title, description, fixture);
        await().atMost(SEARCHABLE_WITHIN).until(() -> titles(search("")).contains(title));
    }

    private void uploadKneesUpAndMossyBanks() {
        uploadSearchable("Knees Up", "A reel and a song from the Frenzy of the Meeting album by Breabach", KNEES_UP_MP3);
        uploadSearchable("Mossy Banks", "Two reels played on the flute", MOSSY_BANKS_MP3);
    }

    private List<TrackDTO> search(String q) {
        return client.get().uri(b -> b.path("/api/tracks/search").queryParam("q", "{q}").build(q))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
    }

    private static List<String> titles(List<TrackDTO> tracks) {
        return tracks.stream().map(TrackDTO::title).toList();
    }
}
