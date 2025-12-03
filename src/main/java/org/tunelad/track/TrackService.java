package org.tunelad.track;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteAllTrackCommand;
import org.tunelad.track.command.PlayTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackService {
    Mono<Void> execute(AddTrackCommand command);

    Flux<TrackDTO> findAll();

    Mono<TrackDTO> findById(String id);

    Publisher<DataBuffer> execute(PlayTrackCommand command);

    Mono<Void> execute(DeleteAllTrackCommand command);

    Flux<TrackDTO> searchTracks(String q);
}
