package org.tunelad.track;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.track.event.NewTrackSaved;
import org.tunelad.track.event.TrackDeleted;
import org.tunelad.track.event.TrackUpdated;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Events the track module publishes. Recorded by a plain listener: events are published from the reactive chain
 * on another thread than the test's, which thread-bound recorders (Spring's {@code ApplicationEvents}, Modulith's
 * {@code PublishedEvents}) do not see.
 */
@Import(TrackEventsTest.Recorder.class)
class TrackEventsTest extends AbstractApiTest {

    static final List<NewTrackSaved> saved = new CopyOnWriteArrayList<>();
    static final List<TrackUpdated> updated = new CopyOnWriteArrayList<>();
    static final List<TrackDeleted> deleted = new CopyOnWriteArrayList<>();

    @BeforeEach
    void forgetEvents() {
        saved.clear();
        updated.clear();
        deleted.clear();
    }

    @Test
    void upload_publishes_new_track_saved_once_with_the_stored_track() {
        upload(Map.of("title", "Knees Up", "description", "A reel and a song",
                "artist", "Breabach", "album", "Frenzy of the Meeting"), KNEES_UP_MP3)
                .expectStatus().is2xxSuccessful();

        // Published within the upload, so recorded by the time the response arrives.
        assertThat(saved).extracting(NewTrackSaved::track).containsExactly(trackTitled("Knees Up"));
    }

    @Test
    void edit_publishes_track_updated_once_with_the_updated_track() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        edit(id, Map.of("title", "Knees Up (live)", "description", "Live"), UPLOADER_TOKEN).expectStatus().isOk();

        assertThat(updated).extracting(TrackUpdated::track).containsExactly(trackTitled("Knees Up (live)"));
    }

    @Test
    void delete_publishes_track_deleted_once_with_its_id() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();

        delete(id, UPLOADER_TOKEN).expectStatus().isNoContent();

        assertThat(deleted).extracting(TrackDeleted::trackId).containsExactly(id);
    }

    @Test
    void refused_or_unknown_changes_publish_nothing() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        var id = trackTitled("Knees Up").id();
        var bob = TestTokens.bearer("bob", "Bob Smith");

        edit(id, Map.of("title", "Bob's", "description", "x"), bob).expectStatus().isForbidden();
        delete(id, bob).expectStatus().isForbidden();
        edit("no-such-track", Map.of("title", "x", "description", "x"), UPLOADER_TOKEN).expectStatus().isNotFound();
        delete("no-such-track", UPLOADER_TOKEN).expectStatus().isNotFound();

        assertThat(updated).isEmpty();
        assertThat(deleted).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {

        @EventListener
        void on(NewTrackSaved event) {
            saved.add(event);
        }

        @EventListener
        void on(TrackUpdated event) {
            updated.add(event);
        }

        @EventListener
        void on(TrackDeleted event) {
            deleted.add(event);
        }
    }
}
