package org.tunelad.track.internal.db;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.tunelad.track.Track;
import org.tunelad.track.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaTracks implements Tracks {

	private final JpaTrackRepository jpaTrackRepository;

	@Override
	public Flux<Track> findAll() {
		return Flux.fromIterable(jpaTrackRepository.findAll());
	}

	@Override
	public Mono<Track> save(Track track) {
		if (track.getId() == null || track.getId().isBlank()) {
			track.setId(UUID.randomUUID().toString());
		}
		return Mono.fromSupplier(() -> jpaTrackRepository.save(track));
	}

	@Override
	public Mono<Track> findById(String id) {
		return jpaTrackRepository.findById(id)
				.map(track -> Mono.fromSupplier(() -> track))
				.orElse(Mono.empty());
	}

	@Override
	public Mono<Void> deleteAll() {
		jpaTrackRepository.deleteAll();
		return Mono.empty();
	}
}
