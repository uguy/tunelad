package org.tunelad.track.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.tunelad.account.AccountRef;
import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.infrastructure.ForbiddenException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.MediaStore;
import org.tunelad.set.SetCatalog;
import org.tunelad.set.SetDTO;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteTrackCommand;
import org.tunelad.track.command.PerformanceInput;
import org.tunelad.track.command.UpdateTrackCommand;
import org.tunelad.track.event.NewTrackSaved;
import org.tunelad.track.event.TrackDeleted;
import org.tunelad.track.event.TrackUpdated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
class DefaultTrackService implements TrackService {

    private final TrackMapper trackMapper;

    private final Tracks tracks;

    private final Artists artists;

    private final MediaStore mediaStore;

    private final SetCatalog sets;

    private final TransactionalEvents events;

    /** For uploads, whose transaction covers only part of the work; the other methods use the annotation. */
    private final TransactionalOperator transactionalOperator;

    DefaultTrackService(TrackMapper trackMapper, Tracks tracks, Artists artists, MediaStore mediaStore,
                        SetCatalog sets, TransactionalEvents events, @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager) {
        this.trackMapper = trackMapper;
        this.tracks = tracks;
        this.artists = artists;
        this.mediaStore = mediaStore;
        this.sets = sets;
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
        // The Set and the Performances are checked before any audio is written: a refused upload stores nothing
        return sets.find(command.setId())
                .switchIfEmpty(Mono.error(() -> new BadRequestException("No set found for id: " + command.setId())))
                .flatMap(set -> store(command, set));
    }

    private Mono<TrackDTO> store(AddTrackCommand command, SetDTO set) {
        var inputs = command.performances() == null ? defaults(set.tunes().size()) : command.performances();
        if (inputs.size() != set.tunes().size()) {
            return Mono.error(new BadRequestException("The set has " + set.tunes().size() + " tunes: one performance each."));
        }
        inputs.forEach(PerformanceInput::validate);
        var track = new Track();
        track.setSetId(set.id());
        track.setTuneSet(set);
        track.setPerformances(new java.util.ArrayList<>(java.util.stream.IntStream.range(0, inputs.size())
                .mapToObj(i -> new Performance(i + 1, set.tunes().get(i).id(), inputs.get(i).offset(),
                        inputs.get(i).cleanKey(), inputs.get(i).cleanNotes(), inputs.get(i).cleanAbc()))
                .toList()));
        // Known up front, so the audio can be stored under the track's id before the track exists
        track.setId(java.util.UUID.randomUUID().toString());
        track.setTitle(command.title());
        track.setAlbum(command.album());
        track.setDescription(command.description());
        track.setFormat(command.format());
        track.setOwner(command.owner());
        var id = track.getId();

        var saveAndAnnounce = artists.findOrCreate(command.artist())
                .doOnNext(track::setArtist)
                .then(Mono.defer(() -> tracks.save(track)))
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

    private static List<PerformanceInput> defaults(int count) {
        return java.util.Collections.nCopies(count, PerformanceInput.DEFAULT);
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
                .doOnNext(track -> changePerformances(track, command.performances()))
                .flatMap(track -> artists.findOrCreate(command.artist())
                        // A blank name has no Artist: the track loses the one it had
                        .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                        .flatMap(artist -> {
                            track.setTitle(command.title());
                            track.setDescription(command.description());
                            track.setArtist(artist.orElse(null));
                            track.setAlbum(command.album());
                            return tracks.save(track);
                        }))
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

    /** Replaces the values of the track's Performances; null leaves them. Fails with 400 before anything changes. */
    private static void changePerformances(Track track, List<PerformanceInput> inputs) {
        if (inputs == null) {
            return;
        }
        if (inputs.size() != track.getPerformances().size()) {
            throw new BadRequestException("The track has " + track.getPerformances().size() + " performances: one entry each.");
        }
        inputs.forEach(PerformanceInput::validate);
        var changed = new java.util.ArrayList<Performance>();
        for (int i = 0; i < inputs.size(); i++) {
            changed.add(track.getPerformances().get(i).with(inputs.get(i).offset(), inputs.get(i).cleanKey(), inputs.get(i).cleanNotes(), inputs.get(i).cleanAbc()));
        }
        track.setPerformances(changed);
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
    public Flux<org.tunelad.track.TunePerformanceDTO> findPerformancesByTune(String tuneId) {
        return tracks.findPerformancesByTune(tuneId);
    }

    @ReactiveTransactional(readOnly = true)
    @Override
    public Mono<TrackDTO> findById(String id) {
        return tracks.findById(id)
//				.switchIfEmpty(Mono.error(new NotFoundException("Not tracks found for id: " + id)))
                .map(trackMapper::toDto);
    }

    @ReactiveTransactional(readOnly = true)
    @Override
    public Flux<TrackDTO> findAllById(java.util.Collection<String> ids) {
        return tracks.findAllById(ids).map(trackMapper::toDto);
    }

}
