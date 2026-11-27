package org.tunelad.playlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.tunelad.playlist.event.PlaylistChanged;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The events the playlist module publishes: one per change, none when nothing changed. */
@Import(PlaylistEventsTest.Recorder.class)
class PlaylistEventsTest extends AbstractPlaylistTest {

    static final List<PlaylistChanged> changed = new CopyOnWriteArrayList<>();

    @BeforeEach
    void forgetEvents() {
        changed.clear();
    }

    @Test
    void create_rename_and_delete_each_publish_once() {
        var alice = accountOf(UPLOADER_TOKEN);

        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        rename(playlist.id(), Map.of("name", "Reels"), UPLOADER_TOKEN).expectStatus().isOk();
        deletePlaylist(playlist.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        var event = new PlaylistChanged(alice.id(), playlist.id());
        assertThat(changed).containsExactly(event, event, event);
    }

    @Test
    void add_remove_and_reorder_each_publish_once_and_no_op_publishes_nothing() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var mossyBanks = uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);
        var alice = accountOf(UPLOADER_TOKEN);
        var playlist = createOk("Session tunes", UPLOADER_TOKEN);
        var event = new PlaylistChanged(alice.id(), playlist.id());
        changed.clear();

        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent(); // again: nothing
        add(playlist.id(), mossyBanks.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        reorder(playlist.id(), List.of(kneesUp.id(), mossyBanks.id()), UPLOADER_TOKEN).expectStatus().isNoContent(); // same order: nothing
        reorder(playlist.id(), List.of(mossyBanks.id(), kneesUp.id()), UPLOADER_TOKEN).expectStatus().isNoContent();
        remove(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        remove(playlist.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent(); // again: nothing

        assertThat(changed).containsExactly(event, event, event, event);
    }

    @Test
    void refused_changes_publish_nothing() {
        var bobs = createOk("Bob's", BOB_TOKEN);
        changed.clear();

        rename(bobs.id(), Map.of("name", "Mine now"), UPLOADER_TOKEN).expectStatus().isNotFound();
        add(bobs.id(), "any", UPLOADER_TOKEN).expectStatus().isNotFound();
        create(Map.of("name", ""), UPLOADER_TOKEN).expectStatus().isBadRequest();
        reorder(bobs.id(), List.of("x"), BOB_TOKEN).expectStatus().isBadRequest();

        assertThat(changed).isEmpty();
    }

    @Test
    void deleting_a_track_publishes_one_event_for_each_playlist_that_held_it() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var alice = accountOf(UPLOADER_TOKEN);
        var bob = accountOf(BOB_TOKEN);
        var first = createOk("First", UPLOADER_TOKEN);
        var second = createOk("Second", UPLOADER_TOKEN);
        var bobs = createOk("Bob's", BOB_TOKEN);
        var untouched = createOk("Untouched", UPLOADER_TOKEN);
        add(first.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(second.id(), kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        add(bobs.id(), kneesUp.id(), BOB_TOKEN).expectStatus().isNoContent();
        changed.clear();

        delete(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(changed).containsExactlyInAnyOrder(
                new PlaylistChanged(alice.id(), first.id()), new PlaylistChanged(alice.id(), second.id()),
                new PlaylistChanged(bob.id(), bobs.id())));
        assertThat(changed).doesNotContain(new PlaylistChanged(alice.id(), untouched.id()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {

        @Bean
        RecordingListener playlistRecordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {

        @EventListener
        void on(PlaylistChanged event) {
            changed.add(event);
        }
    }
}
