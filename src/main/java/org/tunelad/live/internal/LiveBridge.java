package org.tunelad.live.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.comment.event.CommentChanged;
import org.tunelad.favourite.event.FavouriteChanged;
import org.tunelad.media.event.AudioPrepared;
import org.tunelad.media.event.WaveformComputed;
import org.tunelad.playlist.event.PlaylistChanged;
import org.tunelad.search.event.TrackIndexed;
import org.tunelad.set.event.SetChanged;
import org.tunelad.track.event.AlbumChanged;
import org.tunelad.track.event.NewTrackSaved;
import org.tunelad.track.event.TrackDeleted;
import org.tunelad.track.event.TrackUpdated;
import org.tunelad.tune.event.TuneChanged;
import org.tunelad.search.event.TrackUnindexed;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the events of the modules that own the state into live events, published on NATS: for everyone when they
 * concern the shared library (Tracks, Tunes, Sets, Performances, Comments), and for the connections of one account when they concern its favourites or
 * playlists, which are private.
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
    private final LiveMetrics metrics;

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

    /** The audio's key is the id of the track it belongs to: the browser refetches whether it can be played. */
    @ApplicationModuleListener
    void on(AudioPrepared event) {
        publishToEveryone("audio.prepared", event.key());
    }

    /** The track's id: the browser refetches the favourites, which only the account's own connections hear of. */
    @ApplicationModuleListener
    void on(FavouriteChanged event) {
        publish(LiveSubjects.account(event.accountId(), "favourite.changed"), "favourite.changed", event.trackId());
    }

    /** The playlist's id: the browser refetches it, through the API, which only lets its owner read it. */
    @ApplicationModuleListener
    void on(PlaylistChanged event) {
        publish(LiveSubjects.account(event.ownerId(), "playlist.changed"), "playlist.changed", event.playlistId());
    }

    /** The subject's id: the browser refetches its Comments, which are public. */
    @ApplicationModuleListener
    void on(CommentChanged event) {
        publishToEveryone("comment.changed", event.subjectId());
    }

    /** The Tune's id, or none after a bulk import: the browser refetches the Tunes, which are public. */
    @ApplicationModuleListener
    void on(TuneChanged event) {
        publishToEveryone("tune.changed", event.tuneId());
    }

    /** The Set's id: the browser refetches the Sets, which are public. */
    @ApplicationModuleListener
    void on(SetChanged event) {
        publishToEveryone("set.changed", event.setId());
    }

    /**
     * The Track's id, for its Performances, which are public. A Track event is raised in the transaction that wrote
     * the Performances, so a browser refetching on the hint sees them. An edit of the Track's metadata alone also
     * announces it: a harmless extra refetch.
     */
    @ApplicationModuleListener
    void on(NewTrackSaved event) {
        publishToEveryone("performance.changed", event.trackId());
    }

    @ApplicationModuleListener
    void on(TrackUpdated event) {
        publishToEveryone("performance.changed", event.trackId());
    }

    @ApplicationModuleListener
    void on(TrackDeleted event) {
        publishToEveryone("performance.changed", event.trackId());
    }

    /** The Album's id: the browser refetches the Albums, which are public. */
    @ApplicationModuleListener
    void on(AlbumChanged event) {
        publishToEveryone("album.changed", event.albumId());
    }

    private void publishToEveryone(String type, String id) {
        publish(LiveSubjects.library(type), type, id);
    }

    private void publish(String subject, String type, String id) {
        try {
            bus.publish(subject, json.writeValueAsString(new LiveEvent(type, id)));
            metrics.published(type);
        } catch (RuntimeException e) {
            log.warn("A live event {} for {} was dropped", type, id, e);
        }
    }
}
