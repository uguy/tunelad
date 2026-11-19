package org.tunelad.track;

import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.PlayTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackService {
    Mono<Void> execute(AddTrackCommand command);

    Flux<TrackDTO> findAll();

    Mono<TrackDTO> findById(String id);

    /** The audio of a track; errors with {@code NotFoundException} when the track or its audio is missing. */
    Mono<TrackMedia> execute(PlayTrackCommand command);
}
