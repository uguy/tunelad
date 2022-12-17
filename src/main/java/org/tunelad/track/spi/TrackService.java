package org.tunelad.track.spi;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackService {

	Mono<Void> execute(NewTrackCommand command);

	Flux<TrackDTO> findAll();

	Mono<TrackDTO> findById(String id);

	Mono<byte[]> play(String id);

	Mono<Void> deleteAll();
}
