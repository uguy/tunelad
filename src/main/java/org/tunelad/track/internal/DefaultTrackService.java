package org.tunelad.track.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tunelad.account.AccountRef;
import org.tunelad.infrastructure.ForbiddenException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.MediaStore;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteTrackCommand;
import org.tunelad.track.command.UpdateTrackCommand;
import org.tunelad.track.event.NewTrackSaved;
import org.tunelad.track.event.TrackDeleted;
import org.tunelad.track.event.TrackUpdated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@ReactiveTransactional
@RequiredArgsConstructor
class DefaultTrackService implements TrackService {

    private final TrackMapper trackMapper;

    private final Tracks tracks;

    private final MediaStore mediaStore;

    private final TransactionalEvents events;

    @Override
    public Mono<TrackDTO> execute(AddTrackCommand command) {
        var track = new Track();
        track.setTitle(command.title());
        track.setArtist(new Artist(command.artist()));
        track.setAlbum(command.album());
        track.setDescription(command.description());
        track.setFormat(command.format());
        track.setOwner(command.owner());

        return tracks.save(track)
                .flatMap(saved -> mediaStore.store(saved.getId(), command.data(), command.format().mimeType()).thenReturn(saved))
                // Published within the transaction: module listeners only see it once the track is committed
                .map(trackMapper::toDto)
                .flatMap(dto -> events.publish(new NewTrackSaved(dto)).thenReturn(dto));
    }

    @Override
    public Mono<TrackDTO> execute(UpdateTrackCommand command) {
        return changeableTrack(command.id(), command.by())
                .flatMap(track -> {
                    track.setTitle(command.title());
                    track.setDescription(command.description());
                    track.setArtist(new Artist(command.artist()));
                    track.setAlbum(command.album());
                    return tracks.save(track);
                })
                .map(trackMapper::toDto)
                .flatMap(updated -> events.publish(new TrackUpdated(updated)).thenReturn(updated));
    }

    @Override
    public Mono<Void> execute(DeleteTrackCommand command) {
        return changeableTrack(command.id(), command.by())
                // The audio goes now, not on an event: playback only looks at the stored audio
                .flatMap(track -> tracks.delete(track).then(mediaStore.delete(track.getId())))
                .then(events.publish(new TrackDeleted(command.id())));
    }

    /** The track, if it exists (else 404) and the account may change it (else 403). */
    private Mono<Track> changeableTrack(String id, AccountRef account) {
        return tracks.findById(id)
                .switchIfEmpty(Mono.error(() -> new NotFoundException("No track found for id: " + id)))
                .filter(track -> track.isChangeableBy(account))
                .switchIfEmpty(Mono.error(() -> new ForbiddenException(
                        "Account " + account.id() + " may not change track " + id)));
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
