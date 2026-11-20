package org.tunelad.track;

import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteTrackCommand;
import org.tunelad.track.command.UpdateTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackService {
    Mono<Void> execute(AddTrackCommand command);

    /**
     * Replaces the track's metadata and returns the updated track. Fails with {@code NotFoundException} when no track
     * has that id, and {@code ForbiddenException} when the account is neither its owner nor an administrator.
     */
    Mono<TrackDTO> execute(UpdateTrackCommand command);

    /** Deletes the track and its audio. Fails like {@link #execute(UpdateTrackCommand)}. */
    Mono<Void> execute(DeleteTrackCommand command);

    Flux<TrackDTO> findAll();

    Mono<TrackDTO> findById(String id);
}
