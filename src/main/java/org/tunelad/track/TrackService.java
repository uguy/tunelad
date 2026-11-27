package org.tunelad.track;

import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteTrackCommand;
import org.tunelad.track.command.UpdateTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackService {
    /** Stores the track with its audio and returns the stored track. */
    Mono<TrackDTO> execute(AddTrackCommand command);

    /**
     * Replaces the track's metadata and returns the updated track. Fails with {@code NotFoundException} when no track
     * has that id, and {@code ForbiddenException} when the account is neither its owner nor an administrator.
     */
    Mono<TrackDTO> execute(UpdateTrackCommand command);

    /** Deletes the track and its audio. Fails like {@link #execute(UpdateTrackCommand)}. */
    Mono<Void> execute(DeleteTrackCommand command);

    Flux<TrackDTO> findAll();

    /** Every Performance of the Tune across Tracks, ordered by Track title; empty when it was never recorded. */
    Flux<TunePerformanceDTO> findPerformancesByTune(String tuneId);

    Mono<TrackDTO> findById(String id);

    /** The tracks with these ids, in no particular order; ids that match no track are ignored. */
    Flux<TrackDTO> findAllById(java.util.Collection<String> ids);
}
