package org.tunelad.track.internal;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.tunelad.track.TrackDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Tracks {
	Flux<Track> findAll();

	Mono<Track> save(Track track);
	Mono<Track> saveMedia(Track track, Publisher<DataBuffer> media);

	Mono<Track> findById(String id);

	Mono<Void> deleteAll();

    Publisher<DataBuffer> readMedia(String id);

    Flux<Track> searchTracks(String q);
}
