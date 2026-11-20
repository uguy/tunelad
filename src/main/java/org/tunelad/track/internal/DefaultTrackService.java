package org.tunelad.track.internal;

import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
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
class DefaultTrackService implements TrackService {

    private final TrackMapper trackMapper;

    private final Tracks tracks;

    private final MediaStore mediaStore;

    private final TransactionalEvents events;

    /** For uploads, whose transaction covers only part of the work; the other methods use the annotation. */
    private final TransactionalOperator transactionalOperator;

    DefaultTrackService(TrackMapper trackMapper, Tracks tracks, MediaStore mediaStore, TransactionalEvents events,
                        @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager) {
        this.trackMapper = trackMapper;
        this.tracks = tracks;
        this.mediaStore = mediaStore;
        this.events = events;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Stores the audio first, outside any transaction, so a long write does not hold one open; then saves the track
     * and publishes {@link NewTrackSaved} in one transaction. When anything fails, or the client goes away, the
     * transaction rolls back and the audio is deleted, so a failed upload leaves nothing behind (see design.md).
     */
    @Override
    public Mono<TrackDTO> execute(AddTrackCommand command) {
        var track = new Track();
        // Known up front, so the audio can be stored under the track's id before the track exists
        track.setId(new ObjectId().toHexString());
        track.setTitle(command.title());
        track.setArtist(new Artist(command.artist()));
        track.setAlbum(command.album());
        track.setDescription(command.description());
        track.setFormat(command.format());
        track.setOwner(command.owner());
        var id = track.getId();

        var saveAndAnnounce = Mono.defer(() -> tracks.save(track))
                .map(trackMapper::toDto)
                // Published within the transaction: module listeners only see it once the track is committed
                .flatMap(dto -> events.publish(new NewTrackSaved(dto)).thenReturn(dto))
                .as(transactionalOperator::transactional);

        return mediaStore.store(id, command.data(), command.format().mimeType())
                .then(saveAndAnnounce)
                .onErrorResume(error -> deleteAudio(id).then(Mono.error(error)))
                // Nobody waits for the result of a cancelled upload: the deletion runs on its own
                .doOnCancel(() -> deleteAudio(id).subscribe());
    }

    /** Deletes the audio of an upload that failed; a failure to do so is logged, not raised over the first error. */
    private Mono<Void> deleteAudio(String id) {
        return mediaStore.delete(id)
                .doOnError(error -> log.warn("Could not delete the audio of failed upload {}", id, error))
                .onErrorComplete();
    }

    @ReactiveTransactional
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

    @ReactiveTransactional
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
