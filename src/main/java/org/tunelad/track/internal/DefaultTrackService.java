package org.tunelad.track.internal;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteAllTrackCommand;
import org.tunelad.track.command.PlayTrackCommand;
import org.tunelad.track.event.AllTracksDeleted;
import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
class DefaultTrackService implements TrackService {

	private final TrackMapper trackMapper;

	private final Tracks tracks;

	private final ApplicationEventPublisher events;

	@Override
	public Mono<Void> execute(AddTrackCommand command) {
		var track = new Track();
		track.setTitle(command.title());
		track.setArtist(command.artist());
		track.setAlbum(command.album());
		track.setDescription(command.description());
		track.setData(command.data());
		track.setFormat(command.format());
		return tracks.save(track)
				.flatMap(saved -> {
					events.publishEvent(new NewTrackSaved(trackMapper.toDto(saved)));
					return Mono.empty();
				});
	}

	@Override
	public Flux<TrackDTO> findAll() {
		return tracks.findAll().map(trackMapper::toDto);
	}

	@Override
	public Mono<TrackDTO> findById(String id) {
		return tracks.findById(id)
//				.switchIfEmpty(Mono.error(new NotFoundException("Not tracks found for id: " + id)))
				.map(trackMapper::toDto);
	}

	@Override
	public Mono<byte[]> execute(PlayTrackCommand command) {
		return tracks.findById(command.trackId())
				.switchIfEmpty(Mono.error(new NotFoundException("Not tracks found for id: " + command.trackId())))
				.map(Track::getData);
	}

	@Override
	public Mono<Void> execute(DeleteAllTrackCommand command) {
		return tracks.deleteAll()
				.and(Mono.fromSupplier(() -> {
					events.publishEvent(new AllTracksDeleted());
					return Mono.empty();
				}));
	}
}
