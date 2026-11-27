package org.tunelad.playlist;

import org.junit.jupiter.api.Test;
import org.tunelad.track.TrackDTO;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Tests for openspec/specs/playlist (the API): one test per spec scenario, named after it. */
class PlaylistApiTest extends AbstractPlaylistTest {

    // --- Requirement: Create and list my playlists --------------------------------------------

    @Test
    void create_a_playlist() {
        var created = create(Map.of("name", "Session tunes"), UPLOADER_TOKEN)
                .expectStatus().isCreated()
                .expectBody(PlaylistSummary.class).returnResult();
        var body = created.getResponseBody();

        assertThat(body.id()).isNotBlank();
        assertThat(body.name()).isEqualTo("Session tunes");
        assertThat(body.trackCount()).isZero();
        assertThat(created.getResponseHeaders().getLocation()).hasToString("/api/playlists/" + body.id());
    }

    @Test
    void blank_name() {
        create(Map.of("name", "  "), UPLOADER_TOKEN).expectStatus().isBadRequest();
        create(Map.of("name", "x".repeat(81)), UPLOADER_TOKEN).expectStatus().isBadRequest();
        create(Map.of("description", "no name"), UPLOADER_TOKEN).expectStatus().isBadRequest();

        assertThat(count("playlist_playlists", null)).isZero();
    }

    @Test
    void list_my_playlists() {
        createOk("Session tunes", UPLOADER_TOKEN);
        createOk("Slow airs", UPLOADER_TOKEN);
        createOk("Bob's", BOB_TOKEN);

        assertThat(list(UPLOADER_TOKEN)).extracting(PlaylistSummary::name)
                .containsExactlyInAnyOrder("Session tunes", "Slow airs");
        assertThat(list(BOB_TOKEN)).extracting(PlaylistSummary::name).containsExactly("Bob's");
    }

    @Test
    void the_most_recently_changed_playlist_comes_first() {
        var first = createOk("First", UPLOADER_TOKEN);
        createOk("Second", UPLOADER_TOKEN);
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        add(first.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(list(UPLOADER_TOKEN)).extracting(PlaylistSummary::name).containsExactly("First", "Second");
    }

    @Test
    void someone_elses_playlist() {
        var bobs = createOk("Bob's", BOB_TOKEN);
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        read(bobs.id(), UPLOADER_TOKEN).expectStatus().isNotFound();
        rename(bobs.id(), Map.of("name", "Mine now"), UPLOADER_TOKEN).expectStatus().isNotFound();
        add(bobs.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNotFound();
        remove(bobs.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNotFound();
        reorder(bobs.id(), List.of(), UPLOADER_TOKEN).expectStatus().isNotFound();
        deletePlaylist(bobs.id(), UPLOADER_TOKEN).expectStatus().isNotFound();

        assertThat(readOk(bobs.id(), BOB_TOKEN).name()).isEqualTo("Bob's");
        assertThat(readOk(bobs.id(), BOB_TOKEN).tracks()).isEmpty();
    }

    @Test
    void anonymous_request() {
        var mine = createOk("Mine", UPLOADER_TOKEN);

        client.get().uri("/api/playlists").exchange().expectStatus().isUnauthorized()
                .expectHeader().doesNotExist("Location");
        create(Map.of("name", "x"), null).expectStatus().isUnauthorized();
        read(mine.id(), null).expectStatus().isUnauthorized();
        rename(mine.id(), Map.of("name", "x"), null).expectStatus().isUnauthorized();
        deletePlaylist(mine.id(), null).expectStatus().isUnauthorized();
        add(mine.id(), "any", null).expectStatus().isUnauthorized();
        remove(mine.id(), "any", null).expectStatus().isUnauthorized();
        reorder(mine.id(), List.of(), null).expectStatus().isUnauthorized();

        assertThat(count("playlist_playlists", null)).isEqualTo(1);
    }

    // --- Requirement: Read, rename and delete a playlist --------------------------------------

    @Test
    void read_with_tracks_in_order() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        var read = readOk(playlist.id(), UPLOADER_TOKEN);

        assertThat(read.name()).isEqualTo("Session tunes");
        assertThat(read.trackCount()).isEqualTo(2);
        assertThat(read.tracks()).containsExactly(mossyBanks, kneesUp);
    }

    @Test
    void rename() {
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);

        var renamed = rename(playlist.id(), Map.of("name", "Reels", "description", "Fast ones"), UPLOADER_TOKEN)
                .expectStatus().isOk().expectBody(PlaylistSummary.class).returnResult().getResponseBody();

        assertThat(renamed.name()).isEqualTo("Reels");
        assertThat(renamed.description()).isEqualTo("Fast ones");
        assertThat(readOk(playlist.id(), UPLOADER_TOKEN).name()).isEqualTo("Reels");
    }

    @Test
    void rename_with_a_blank_name_changes_nothing() {
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);

        rename(playlist.id(), Map.of("name", ""), UPLOADER_TOKEN).expectStatus().isBadRequest();

        assertThat(readOk(playlist.id(), UPLOADER_TOKEN).name()).isEqualTo("Session tunes");
    }

    @Test
    void delete_keeps_the_tracks() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        deletePlaylist(playlist.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        read(playlist.id(), UPLOADER_TOKEN).expectStatus().isNotFound();
        assertThat(count("playlist_items", null)).isZero();
        assertThat(listTracks()).containsExactly(kneesUp);
    }

    // --- Requirement: Add, remove and reorder tracks in a playlist ----------------------------

    @Test
    void add_a_track() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);

        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(readOk(playlist.id(), UPLOADER_TOKEN).tracks()).containsExactly(kneesUp);
    }

    @Test
    void adding_twice() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(readOk(playlist.id(), UPLOADER_TOKEN).tracks()).containsExactly(kneesUp);
    }

    @Test
    void unknown_track() {
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);

        add(playlist.id(), "unknown", UPLOADER_TOKEN).expectStatus().isNotFound();

        assertThat(count("playlist_items", null)).isZero();
    }

    @Test
    void remove_a_track() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(playlist.id(), mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        remove(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        remove(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(readOk(playlist.id(), UPLOADER_TOKEN).tracks()).containsExactly(mossyBanks);
    }

    @Test
    void a_track_added_after_a_removal_goes_last() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(playlist.id(), mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        remove(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(titles(playlist.id(), UPLOADER_TOKEN)).containsExactly("Mossy Banks", "Knees Up");
    }

    @Test
    void reorder() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(playlist.id(), mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        reorder(playlist.id(), List.of(mossyBanks.id(), kneesUp.id()), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(titles(playlist.id(), UPLOADER_TOKEN)).containsExactly("Mossy Banks", "Knees Up");
    }

    @Test
    void reorder_with_a_wrong_list() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(playlist.id(), mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        reorder(playlist.id(), List.of(kneesUp.id()), UPLOADER_TOKEN).expectStatus().isBadRequest();
        reorder(playlist.id(), List.of(kneesUp.id(), kneesUp.id()), UPLOADER_TOKEN).expectStatus().isBadRequest();
        reorder(playlist.id(), List.of(kneesUp.id(), "other"), UPLOADER_TOKEN).expectStatus().isBadRequest();

        assertThat(titles(playlist.id(), UPLOADER_TOKEN)).containsExactly("Knees Up", "Mossy Banks");
    }

    // --- Requirement: A deleted track leaves every playlist -----------------------------------

    @Test
    void track_deleted() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        var slowAir = uploadOk("Slow Air", "A slow air", KNEES_UP_MP3);
        var mine = createOk("Session tunes", UPLOADER_TOKEN);
        var bobs = createOk("Bob's", BOB_TOKEN);
        for (var track : List.of(kneesUp, mossyBanks, slowAir)) {
            add(mine.id(), track.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        }
        add(bobs.id(), mossyBanks.id(), BOB_TOKEN).expectStatus().isNoContent();

        delete(mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(titles(mine.id(), UPLOADER_TOKEN)).containsExactly("Knees Up", "Slow Air");
            assertThat(readOk(bobs.id(), BOB_TOKEN).tracks()).isEmpty();
        });
        // The order of the others is intact: a track added now goes after them
        var another = uploadOk("Another", "Another", MOSSY_BANKS_MP3);
        add(mine.id(), another.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        assertThat(titles(mine.id(), UPLOADER_TOKEN)).containsExactly("Knees Up", "Slow Air", "Another");
    }

    @Test
    void tracks_in_the_playlist_are_complete_tracks() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        TrackDTO track = readOk(playlist.id(), UPLOADER_TOKEN).tracks().get(0);

        assertThat(track.owner()).isEqualTo(kneesUp.owner());
        assertThat(track.format()).isEqualTo(kneesUp.format());
    }
}
