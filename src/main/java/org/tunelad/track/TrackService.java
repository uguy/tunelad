package org.tunelad.track;

import org.tunelad.track.command.AddCatalogueTrack;
import org.tunelad.track.command.AttachAudio;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.ApplyTags;
import org.tunelad.track.command.AttachSet;
import org.tunelad.track.command.DeleteTrackCommand;
import org.tunelad.track.command.ProposePerformances;
import org.tunelad.track.command.UpdateTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrackService {
    /** Stores the track with its audio and returns the stored track. */
    Mono<TrackDTO> execute(AddTrackCommand command);

    /**
     * Replaces the track's metadata and returns the updated track. Fails with {@code NotFoundException} when no track
     * has that id, and {@code ForbiddenException} when the account is neither its owner nor an administrator.
     */
    Mono<TrackDTO> execute(UpdateTrackCommand command);

    /** Deletes the track and its audio. Fails like {@link #execute(UpdateTrackCommand)}. */
    Mono<Void> execute(DeleteTrackCommand command);

    /**
     * Applies Identification's proposals to the Track's Performances that no Account corrected, marking them
     * identified, and returns the track. When the analysis finished, the Performances still pending become "nothing
     * found". Proposals for unknown positions are ignored. Completes empty when the
     * track no longer exists (it may have been deleted while the analysis ran).
     */
    Mono<TrackDTO> execute(ProposePerformances command);

    /**
     * Fills the Track's title, artist, album and description that no Account provided from the tags read from its
     * audio, and returns the track. Completes empty when nothing changed, or when the track no longer exists.
     */
    Mono<TrackDTO> execute(ApplyTags command);

    /**
     * Gives a Track without a Set the Set of the command, with its Performances identified at the offsets, and returns
     * the track. Completes empty, changing nothing, when the track no longer exists or already has a Set.
     */
    Mono<TrackDTO> execute(AttachSet command);

    /**
     * Puts a Catalogue Track, a Track with no audio yet, at a position of an Album, with one Performance per listed
     * tune. It gets a Set only when one to twenty tunes are listed and all of them resolve. Fails with
     * {@code NotFoundException} when the Album does not exist, {@code BadRequestException} for a blank title, a
     * position under 1 or a listed tune with a blank name, and 409 Conflict when the position is taken. Announces
     * the change with an {@code AlbumChanged} event.
     */
    Mono<TrackDTO> execute(AddCatalogueTrack command);

    /**
     * Stores the audio of a Catalogue Track like an upload does (nothing is left behind when it fails), makes the
     * owner its Owner and turns it into an ordinary Track keeping its Album, position, title, Performances and Set.
     * Fails with {@code NotFoundException} for an unknown track and 409 Conflict when the track is not a Catalogue
     * Track or already has audio. Announces the track like a new upload, so its audio is prepared, tagged and indexed.
     */
    Mono<TrackDTO> execute(AttachAudio command);

    /**
     * The Catalogue Tracks of the Track's Artist whose title matches its own ignoring case, punctuation and
     * surrounding whitespace (or the tune names listed for them, joined). Empty when the Track has no Artist or is a
     * Catalogue Track itself; fails with {@code NotFoundException} for an unknown track.
     */
    Flux<TrackDTO> catalogueSuggestions(String trackId);

    Flux<TrackDTO> findAll();

    /** Every Performance of the Tune across Tracks, ordered by Track title; empty when it was never recorded. */
    Flux<TunePerformanceDTO> findPerformancesByTune(String tuneId);

    Mono<TrackDTO> findById(String id);

    /** The tracks with these ids, in no particular order; ids that match no track are ignored. */
    Flux<TrackDTO> findAllById(java.util.Collection<String> ids);
}
