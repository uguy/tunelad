package org.tunelad.track.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackMedia;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.PlayTrackCommand;
import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@ReactiveTransactional
@RequiredArgsConstructor
class DefaultTrackService implements TrackService {

    private final TrackMapper trackMapper;

    private final Tracks tracks;

    private final TransactionalEvents events;

    @Override
    public Mono<Void> execute(AddTrackCommand command) {
        var track = new Track();
        track.setTitle(command.title());
        track.setArtist(new Artist(command.artist()));
        track.setAlbum(command.album());
        track.setDescription(command.description());
        track.setFormat(command.format());

        return tracks.save(track)
                .flatMap(saved -> tracks.saveMedia(saved, command.data()))
                // Published within the transaction: module listeners only see it once the track is committed
                .flatMap(saved -> events.publish(new NewTrackSaved(trackMapper.toDto(saved))));
    }

    @Override
    public Mono<TrackMedia> execute(PlayTrackCommand command) {
        return tracks.findById(command.trackId())
                .flatMap(track -> tracks.readMedia(track.getId()))
                .switchIfEmpty(Mono.error(new NotFoundException("No track audio found for id: " + command.trackId())));
    }

    @ReactiveTransactional(readOnly = true)
    @Override
    public Flux<TrackDTO> findAll() {
        return tracks.findAll().map(trackMapper::toDto);
    }

    @ReactiveTransactional(readOnly = true)
    @Override
    public Mono<TrackDTO> findById(String id) {
        return tracks.findById(id)
//				.switchIfEmpty(Mono.error(new NotFoundException("Not tracks found for id: " + id)))
                .map(trackMapper::toDto);
    }

}
