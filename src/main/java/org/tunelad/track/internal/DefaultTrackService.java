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
import org.tunelad.track.command.ApplyTags;
import org.tunelad.track.command.AttachSet;
import org.tunelad.track.command.DeleteTrackCommand;
import org.tunelad.track.command.PerformanceInput;
import org.tunelad.track.command.ProposePerformances;
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

    private static final int TITLE_MAX = 80;

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
        if (!org.springframework.util.StringUtils.hasText(command.setId())) {
            if (command.performances() != null) {
                return Mono.error(new BadRequestException("Performances need a set."));
            }
            return store(command, null);
        }
        return sets.find(command.setId())
                .switchIfEmpty(Mono.error(() -> new BadRequestException("No set found for id: " + command.setId())))
                .flatMap(set -> store(command, set));
    }

    private Mono<TrackDTO> store(AddTrackCommand command, SetDTO set) {
        var track = new Track();
        if (set != null) {
            var inputs = command.performances() == null ? defaults(set.tunes().size()) : command.performances();
            if (inputs.size() != set.tunes().size()) {
                return Mono.error(new BadRequestException("The set has " + set.tunes().size() + " tunes: one performance each."));
            }
            inputs.forEach(PerformanceInput::validate);
            track.setSetId(set.id());
            track.setTuneSet(set);
            track.setPerformances(performancesOf(set, inputs));
        }

        // Known up front, so the audio can be stored under the track's id before the track exists
        track.setId(java.util.UUID.randomUUID().toString());
        track.setTitle(titleOf(command));
        track.setAlbum(blankToNull(command.album()));
        track.setDescription(blankToNull(command.description()));
        track.setProvidedTextFields(providedTextFields(command));
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

    private static List<Performance> performancesOf(SetDTO set, List<PerformanceInput> inputs) {
        return new java.util.ArrayList<>(java.util.stream.IntStream.range(0, inputs.size())
                .mapToObj(i -> new Performance(i + 1, set.tunes().get(i).id(), inputs.get(i).offset(),
                        inputs.get(i).cleanKey(), inputs.get(i).cleanNotes(), inputs.get(i).cleanAbc(),
                        org.tunelad.track.IdentificationState.PENDING))
                .toList());
    }

    /** The title given, else the file name without its extension, cut to the title limit; "Untitled" when nothing is left. */
    private static String titleOf(AddTrackCommand command) {
        var given = blankToNull(command.title());
        if (given != null) {
            return given;
        }
        var name = command.fileName() == null ? "" : command.fileName().strip();
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        var dot = name.lastIndexOf('.');
        name = (dot > 0 ? name.substring(0, dot) : name).strip();
        return name.isEmpty() ? "Untitled" : name.substring(0, Math.min(name.length(), TITLE_MAX));
    }

    private static List<String> providedTextFields(AddTrackCommand command) {
        var provided = new java.util.ArrayList<String>();
        if (blankToNull(command.title()) != null) provided.add("title");
        if (blankToNull(command.description()) != null) provided.add("description");
        if (blankToNull(command.artist()) != null) provided.add("artist");
        if (blankToNull(command.album()) != null) provided.add("album");
        return provided;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
                            track.setDescription(blankToNull(command.description()));
                            track.setArtist(artist.orElse(null));
                            track.setAlbum(blankToNull(command.album()));
                            // Whatever an edit leaves, tags read from the audio must not take it back
                            track.setProvidedTextFields(new java.util.ArrayList<>(List.of("title", "description", "artist", "album")));
                            return tracks.save(track);
                        }))
                .map(trackMapper::toDto)
                .flatMap(updated -> events.publish(new TrackUpdated(updated)).thenReturn(updated));
    }

    /**
     * Locks the track's row for the length of the transaction, so that an Account's edit waits for the proposals (or
     * the other way round) instead of one overwriting the other, and writes only the Performances: the track's own
     * row, and its version, stay as they are, so an edit that read the track meanwhile does not fail.
     */
    @ReactiveTransactional
    @Override
    public Mono<TrackDTO> execute(ProposePerformances command) {
        var proposals = command.proposals().stream()
                .collect(java.util.stream.Collectors.toMap(ProposePerformances.Proposal::position, ProposePerformances.Proposal::startOffsetSeconds, (first, second) -> second));
        return tracks.findByIdForUpdate(command.trackId())
                .flatMap(track -> {
                    var proposed = track.getPerformances().stream()
                            .map(p -> proposals.containsKey(p.position()) ? p.proposed(proposals.get(p.position()))
                                    : command.searched().contains(p.position()) ? p.notFound() : p)
                            .toList();
                    if (proposed.equals(track.getPerformances())) {
                        return Mono.empty();
                    }
                    track.setPerformances(new java.util.ArrayList<>(proposed));
                    return tracks.savePerformances(track).thenReturn(track);
                })
                .map(trackMapper::toDto)
                .flatMap(updated -> events.publish(new TrackUpdated(updated)).thenReturn(updated));
    }

    /**
     * Locks the track's row like {@link #execute(ProposePerformances)}, so that of two analyses, or an analysis and a
     * Set given meanwhile, the first wins and the others find a Set and do nothing.
     */
    @ReactiveTransactional
    @Override
    public Mono<TrackDTO> execute(AttachSet command) {
        return tracks.findByIdForUpdate(command.trackId())
                .filter(track -> track.getSetId() == null)
                .flatMap(track -> sets.find(command.setId())
                        .switchIfEmpty(Mono.error(() -> new IllegalStateException("No set found for id: " + command.setId())))
                        .flatMap(set -> {
                            if (set.tunes().size() != command.startOffsets().size()) {
                                return Mono.error(new IllegalArgumentException("The set has " + set.tunes().size()
                                        + " tunes: one start offset each."));
                            }
                            var performances = performancesOf(set, defaults(set.tunes().size())).stream()
                                    .map(p -> p.proposed(command.startOffsets().get(p.position() - 1)))
                                    .toList();
                            track.setSetId(set.id());
                            track.setTuneSet(set);
                            track.setPerformances(new java.util.ArrayList<>(performances));
                            return tracks.save(track);
                        }))
                .map(trackMapper::toDto)
                .flatMap(updated -> events.publish(new TrackUpdated(updated)).thenReturn(updated));
    }

    /**
     * Locks the track's row like {@link #execute(ProposePerformances)}: an edit either ran first, and made every field
     * provided, or runs after and replaces what the tags set.
     */
    @ReactiveTransactional
    @Override
    public Mono<TrackDTO> execute(ApplyTags command) {
        return tracks.findByIdForUpdate(command.trackId())
                .flatMap(track -> {
                    var provided = track.getProvidedTextFields();
                    var changed = false;
                    var title = blankToNull(command.title());
                    if (title != null && !provided.contains("title")) {
                        title = title.strip().substring(0, Math.min(title.strip().length(), TITLE_MAX));
                        changed |= !title.equals(track.getTitle());
                        track.setTitle(title);
                    }
                    var description = blankToNull(command.description());
                    if (description != null && !provided.contains("description")) {
                        changed |= !description.equals(track.getDescription());
                        track.setDescription(description);
                    }
                    var album = blankToNull(command.album());
                    if (album != null && !provided.contains("album")) {
                        changed |= !album.equals(track.getAlbum());
                        track.setAlbum(album);
                    }
                    var artistName = blankToNull(command.artist());
                    if (artistName == null || provided.contains("artist")) {
                        return changed ? tracks.save(track) : Mono.<Track>empty();
                    }
                    var anyChange = changed;
                    return artists.findOrCreate(artistName).flatMap(artist -> {
                        var artistChanged = track.getArtist() == null || !track.getArtist().id().equals(artist.id());
                        track.setArtist(artist);
                        return anyChange || artistChanged ? tracks.save(track) : Mono.<Track>empty();
                    });
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
