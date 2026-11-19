package org.tunelad.search;

import org.junit.jupiter.api.Test;
import org.tunelad.AbstractApiTest;
import org.tunelad.track.TrackDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for openspec/specs/search: one test per spec scenario, named after it.
 * Search is served by the track module today (GET /api/tracks/search); these tests live in the
 * search package to mirror the capability and its future module.
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
        uploadOk("Mossy Banks", "Two reels played on the flute", MOSSY_BANKS_MP3);
        uploadOk("Fiddle Jig", "A lively fiddle jig for the fiddle", KNEES_UP_MP3);

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

    // --- helpers -----------------------------------------------------------------------------

    private void uploadKneesUpAndMossyBanks() {
        uploadOk("Knees Up", "A reel and a song from the Frenzy of the Meeting album by Breabach", KNEES_UP_MP3);
        uploadOk("Mossy Banks", "Two reels played on the flute", MOSSY_BANKS_MP3);
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
