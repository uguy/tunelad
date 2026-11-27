package org.tunelad.identification.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.media.MediaTags;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.ApplyTags;
import org.tunelad.track.event.NewTrackSaved;

/**
 * Reads the tags of a Track's audio once it is saved, and lets the Track take what no Account provided. It runs after
 * the upload's transaction committed, outside the request: the response never waits for it.
 */
@Slf4j
@Component
class TagsOnUpload {

    private final MediaTags tags;
    private final TrackService tracks;

    TagsOnUpload(MediaTags tags, TrackService tracks) {
        this.tags = tags;
        this.tracks = tracks;
    }

    /** A failure is logged, not raised: the Track is fine without its tags, and the event would otherwise be retried. */
    @ApplicationModuleListener
    void on(NewTrackSaved event) {
        var id = event.track().id();
        try {
            var read = tags.read(id).block();
            if (read != null) {
                tracks.execute(new ApplyTags(id, read.title(), read.artist(), read.album(), read.comment())).block();
            }
        } catch (RuntimeException error) {
            log.warn("Reading the tags of track {} failed: {}", id, error.toString());
        }
    }
}
