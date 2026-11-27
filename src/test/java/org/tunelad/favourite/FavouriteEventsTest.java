package org.tunelad.favourite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.tunelad.favourite.event.FavouriteChanged;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The events the favourite module publishes: one per change, none when nothing changed. */
@Import(FavouriteEventsTest.Recorder.class)
class FavouriteEventsTest extends AbstractFavouriteTest {

    static final List<FavouriteChanged> changed = new CopyOnWriteArrayList<>();

    @BeforeEach
    void forgetEvents() {
        changed.clear();
    }

    @Test
    void marking_publishes_once_and_marking_again_publishes_nothing() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var alice = accountOf(UPLOADER_TOKEN);

        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(changed).containsExactly(new FavouriteChanged(alice.id(), kneesUp.id()));
    }

    @Test
    void unmarking_publishes_once_and_unmarking_a_non_favourite_publishes_nothing() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var alice = accountOf(UPLOADER_TOKEN);
        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        changed.clear();

        unmark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        unmark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(changed).containsExactly(new FavouriteChanged(alice.id(), kneesUp.id()));
    }

    @Test
    void a_refused_mark_publishes_nothing() {
        mark("unknown", UPLOADER_TOKEN).expectStatus().isNotFound();

        assertThat(changed).isEmpty();
    }

    @Test
    void deleting_a_track_publishes_one_event_for_each_account_that_had_it() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var alice = accountOf(UPLOADER_TOKEN);
        var bob = accountOf(BOB_TOKEN);
        mark(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();
        mark(kneesUp.id(), BOB_TOKEN).expectStatus().isNoContent();
        changed.clear();

        delete(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(changed).containsExactlyInAnyOrder(
                new FavouriteChanged(alice.id(), kneesUp.id()), new FavouriteChanged(bob.id(), kneesUp.id())));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {

        @Bean
        RecordingListener favouriteRecordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {

        @EventListener
        void on(FavouriteChanged event) {
            changed.add(event);
        }
    }
}
