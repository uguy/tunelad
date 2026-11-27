package org.tunelad.favourite.internal;

import org.springframework.stereotype.Service;
import org.tunelad.account.AccountRef;
import org.tunelad.favourite.event.FavouriteChanged;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;
import java.util.stream.Collectors;

/** Marks, unmarks and lists an account's favourite tracks. */
@Service
public class FavouriteService {

    private final Favourites favourites;
    private final TrackService tracks;
    private final TransactionalEvents events;

    FavouriteService(Favourites favourites, TrackService tracks, TransactionalEvents events) {
        this.favourites = favourites;
        this.tracks = tracks;
        this.events = events;
    }

    /**
     * Marks the track as a favourite of the account; marking it again changes nothing. Fails with
     * {@link NotFoundException} when no track has that id. Announces the change, once it is committed, only when
     * there was one.
     */
    @ReactiveTransactional
    public Mono<Void> mark(AccountRef account, String trackId) {
        return tracks.findById(trackId)
                .switchIfEmpty(Mono.error(() -> new NotFoundException("No track found for id: " + trackId)))
                .then(favourites.add(account.id(), trackId))
                .filter(Boolean::booleanValue)
                .flatMap(added -> events.publish(new FavouriteChanged(account.id(), trackId)));
    }

    /** Removes the track from the account's favourites; unmarking a track that is not one changes nothing. */
    @ReactiveTransactional
    public Mono<Void> unmark(AccountRef account, String trackId) {
        return favourites.remove(account.id(), trackId)
                .filter(Boolean::booleanValue)
                .flatMap(removed -> events.publish(new FavouriteChanged(account.id(), trackId)));
    }

    /** The account's favourite tracks, most recently marked first; tracks that no longer exist are left out. */
    @ReactiveTransactional(readOnly = true)
    public Flux<TrackDTO> list(AccountRef account) {
        return favourites.trackIdsOf(account.id()).collectList()
                .flatMapMany(ids -> tracks.findAllById(ids)
                        .collect(Collectors.toMap(TrackDTO::id, Function.identity()))
                        .flatMapMany(byId -> Flux.fromIterable(ids).mapNotNull(byId::get)));
    }
}
