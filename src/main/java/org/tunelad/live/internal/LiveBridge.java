package org.tunelad.live.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.media.event.WaveformComputed;
import org.tunelad.search.event.TrackIndexed;
import org.tunelad.search.event.TrackUnindexed;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the events of the modules that own the state into live events for everyone, published on NATS.
 * <p>
 * It listens to {@code search} and {@code media}, which announce a change once it is written, and not to the events
 * that started it: a browser told earlier could refetch the list before the change is in it. Like every module
 * listener it runs on a task executor thread, after the transaction that raised the event committed.
 * <p>
 * Live events are best effort: a failure here is logged and swallowed, so the registry never keeps the event
 * to republish it later, when nobody wants the hint any more.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LiveBridge {

    private final LiveBus bus;
    private final JsonMapper json;

    @ApplicationModuleListener
    void on(TrackIndexed event) {
        publishToEveryone("track.indexed", event.trackId());
    }

    @ApplicationModuleListener
    void on(TrackUnindexed event) {
        publishToEveryone("track.unindexed", event.trackId());
    }

    /** The audio's key is the id of the track it belongs to. */
    @ApplicationModuleListener
    void on(WaveformComputed event) {
        publishToEveryone("waveform.computed", event.key());
    }

    private void publishToEveryone(String type, String id) {
        try {
            bus.publish(LiveSubjects.library(type), json.writeValueAsString(new LiveEvent(type, id)));
        } catch (RuntimeException e) {
            log.warn("A live event {} for {} was dropped", type, id, e);
        }
    }
}
