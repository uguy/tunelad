package org.tunelad.track;

import org.tunelad.track.command.AddTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackService {
    Mono<Void> execute(AddTrackCommand command);

    Flux<TrackDTO> findAll();

    Mono<TrackDTO> findById(String id);
}
