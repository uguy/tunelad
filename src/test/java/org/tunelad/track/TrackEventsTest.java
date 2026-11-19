package org.tunelad.track;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.tunelad.AbstractApiTest;
import org.tunelad.track.event.NewTrackSaved;

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

    @BeforeEach
    void forgetEvents() {
        saved.clear();
    }

    @Test
    void upload_publishes_new_track_saved_once_with_the_stored_track() {
        upload(Map.of("title", "Knees Up", "description", "A reel and a song",
                "artist", "Breabach", "album", "Frenzy of the Meeting"), KNEES_UP_MP3)
                .expectStatus().is2xxSuccessful();

        // Published within the upload, so recorded by the time the response arrives.
        assertThat(saved).extracting(NewTrackSaved::track).containsExactly(trackTitled("Knees Up"));
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
    }
}
