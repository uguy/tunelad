package org.tunelad.track.internal;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.tunelad.track.TrackMedia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Tracks {
	Flux<Track> findAll();

	Mono<Track> save(Track track);
	Mono<Track> saveMedia(Track track, Publisher<DataBuffer> media);

	Mono<Track> findById(String id);

    /** The stored audio of the track with that id, or empty when it has none. */
    Mono<TrackMedia> readMedia(String id);
}
