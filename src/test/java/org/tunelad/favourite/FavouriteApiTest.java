package org.tunelad.favourite;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Tests for openspec/specs/favourite (the API): one test per spec scenario, named after it. */
class FavouriteApiTest extends AbstractFavouriteTest {

    // --- Requirement: Mark and unmark a favourite track ---------------------------------------

    @Test
    void mark_a_favourite() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(favourites(UPLOADER_TOKEN)).containsExactly(kneesUp);
    }

    @Test
    void marking_twice_changes_nothing() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(favourites(UPLOADER_TOKEN)).containsExactly(kneesUp);
    }

    @Test
    void unmark_a_favourite() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        unmark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(favourites(UPLOADER_TOKEN)).isEmpty();
    }

    @Test
    void unmarking_a_track_that_is_not_a_favourite() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        unmark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
    }

    @Test
    void unknown_track() {
        mark("unknown", UPLOADER_TOKEN).expectStatus().isNotFound();

        assertThat(count("favourite_favourites", null)).isZero();
    }

    @Test
    void anonymous_request() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        mark(kneesUp.id(), null).expectStatus().isUnauthorized();
        unmark(kneesUp.id(), null).expectStatus().isUnauthorized();

        assertThat(count("favourite_favourites", null)).isZero();
    }

    @Test
    void favourites_are_private() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(favourites(BOB_TOKEN)).isEmpty();
    }

    // --- Requirement: List my favourite tracks ------------------------------------------------

    @Test
    void most_recent_first() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);

        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        mark(mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(favourites(UPLOADER_TOKEN)).containsExactly(mossyBanks, kneesUp);
    }

    @Test
    void no_favourites() {
        assertThat(favourites(BOB_TOKEN)).isEmpty();
    }

    @Test
    void anonymous_list_is_unauthorized_and_not_a_redirect() {
        client.get().uri("/api/me/favourites").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist("Location");
    }

    // --- Requirement: A deleted track leaves every favourites list ----------------------------

    @Test
    void track_deleted() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        mark(kneesUp.id(), BOB_TOKEN).expectStatus().isNoContent();
        mark(mossyBanks.id(), BOB_TOKEN).expectStatus().isNoContent();

        delete(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        // The cleanup runs after the deletion committed, on another thread
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(favourites(UPLOADER_TOKEN)).isEmpty();
            assertThat(favourites(BOB_TOKEN)).containsExactly(mossyBanks);
            assertThat(count("favourite_favourites", "track_id = '" + kneesUp.id() + "'")).isZero();
        });
    }
}
