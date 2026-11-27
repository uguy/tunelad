package org.tunelad.identification.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.track.event.NewTrackSaved;

/** Starts Identification once a Track is saved, outside the upload: the response never waits for it. */
@Slf4j
@Component
class IdentificationOnUpload {

    private final IdentificationService identification;

    IdentificationOnUpload(IdentificationService identification) {
        this.identification = identification;
    }

    /**
     * Runs on a task executor thread, after the upload's transaction committed, so it may block until the analysis
     * ends. A failure is logged, not raised: the Track is fine without proposals, and the owner can run it again.
     */
    @ApplicationModuleListener
    void on(NewTrackSaved event) {
        var id = event.track().id();
        try {
            identification.identify(id).block();
        } catch (RuntimeException error) {
            log.warn("Identification of track {} failed: {}", id, error.toString());
        }
    }
}
