package org.tunelad.comment.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.track.event.TrackDeleted;

/**
 * Takes the Comments of a deleted Track away. Like every module listener it runs on a task executor thread after
 * the deletion committed, so the blocking wait never holds an event loop thread. It is idempotent: the registry
 * delivers at least once, and a second run finds nothing to remove.
 */
@Component
@RequiredArgsConstructor
class CommentCleanup {

    private final CommentService comments;

    @ApplicationModuleListener
    void on(TrackDeleted event) {
        comments.deleteWithTrack(event.trackId()).block();
    }
}
