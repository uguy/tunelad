package org.tunelad.playlist.internal;

import org.springframework.stereotype.Service;
import org.tunelad.account.AccountRef;
import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.playlist.PlaylistDTO;
import org.tunelad.playlist.PlaylistInput;
import org.tunelad.playlist.PlaylistSummary;
import org.tunelad.playlist.event.PlaylistChanged;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An account's playlists. A playlist that belongs to someone else is reported as not found, as if it did not exist,
 * so that nobody can tell playlist ids apart from guesses.
 */
@Service
public class PlaylistService {

    private final Playlists playlists;
    private final TrackService tracks;
    private final TransactionalEvents events;

    PlaylistService(Playlists playlists, TrackService tracks, TransactionalEvents events) {
        this.playlists = playlists;
        this.tracks = tracks;
        this.events = events;
    }

    @ReactiveTransactional
    public Mono<PlaylistSummary> create(AccountRef account, PlaylistInput input) {
        return playlists.insert(account.id(), input.name(), input.description())
                .flatMap(id -> changed(account.id(), id).then(playlists.findOwned(account.id(), id)));
    }

    @ReactiveTransactional(readOnly = true)
    public Flux<PlaylistSummary> list(AccountRef account) {
        return playlists.findByOwner(account.id());
    }

    @ReactiveTransactional(readOnly = true)
    public Mono<PlaylistDTO> get(AccountRef account, String id) {
        return owned(account, id).flatMap(summary -> playlists.trackIds(id).collectList()
                .flatMap(ids -> tracksInOrder(ids).map(list -> new PlaylistDTO(summary.id(), summary.name(),
                        summary.description(), list.size(), summary.updatedAt(), list))));
    }

    @ReactiveTransactional
    public Mono<PlaylistSummary> update(AccountRef account, String id, PlaylistInput input) {
        return owned(account, id)
                .then(playlists.rename(id, input.name(), input.description()))
                .then(changed(account.id(), id))
                .then(playlists.findOwned(account.id(), id));
    }

    /** Deletes the playlist, not its tracks. */
    @ReactiveTransactional
    public Mono<Void> delete(AccountRef account, String id) {
        return owned(account, id)
                .then(playlists.delete(id))
                .then(changed(account.id(), id));
    }

    /** Appends the track to the playlist; a playlist that holds it already is left as it is. */
    @ReactiveTransactional
    public Mono<Void> addTrack(AccountRef account, String id, String trackId) {
        return owned(account, id)
                .then(tracks.findById(trackId))
                .switchIfEmpty(Mono.error(() -> new NotFoundException("No track found for id: " + trackId)))
                .then(playlists.addItem(id, trackId))
                .filter(Boolean::booleanValue)
                .flatMap(added -> playlists.touch(id).then(changed(account.id(), id)));
    }

    /** Removes the track from the playlist; a playlist that does not hold it is left as it is. */
    @ReactiveTransactional
    public Mono<Void> removeTrack(AccountRef account, String id, String trackId) {
        return owned(account, id)
                .then(playlists.removeItem(id, trackId))
                .filter(Boolean::booleanValue)
                .flatMap(removed -> playlists.touch(id).then(changed(account.id(), id)));
    }

    /**
     * Puts the playlist's tracks in that order. The ids must be exactly the playlist's tracks, each once, else
     * nothing changes and the request is refused.
     */
    @ReactiveTransactional
    public Mono<Void> reorder(AccountRef account, String id, List<String> orderedIds) {
        return owned(account, id).then(playlists.trackIds(id).collectList()).flatMap(current -> {
            if (orderedIds.size() != current.size() || !new HashSet<>(orderedIds).equals(new HashSet<>(current))) {
                return Mono.error(new BadRequestException(
                        "The new order must list each track of the playlist exactly once"));
            }
            if (orderedIds.equals(current)) {
                return Mono.empty();
            }
            return Flux.range(0, orderedIds.size())
                    .concatMap(position -> playlists.setPosition(id, orderedIds.get(position), position))
                    .then(playlists.touch(id))
                    .then(changed(account.id(), id));
        });
    }

    private Mono<PlaylistSummary> owned(AccountRef account, String id) {
        return playlists.findOwned(account.id(), id)
                .switchIfEmpty(Mono.error(() -> new NotFoundException("No playlist found for id: " + id)));
    }

    /** The tracks with those ids, in that order; tracks that no longer exist are left out. */
    private Mono<List<TrackDTO>> tracksInOrder(List<String> ids) {
        return tracks.findAllById(ids)
                .collect(Collectors.toMap(TrackDTO::id, Function.identity()))
                .map(byId -> ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList());
    }

    private Mono<Void> changed(String ownerId, String playlistId) {
        return events.publish(new PlaylistChanged(ownerId, playlistId));
    }
}
