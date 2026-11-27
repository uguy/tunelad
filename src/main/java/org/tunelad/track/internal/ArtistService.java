package org.tunelad.track.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.track.ArtistDTO;
import org.tunelad.track.ArtistKind;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * The rules of Artist kinds: only a Band has members, its members are Musicians, a Band is nobody's member, and
 * an Artist that has members, or is a member, keeps a kind that allows it.
 */
@Service
@RequiredArgsConstructor
public class ArtistService {

    private final Artists artists;

    @ReactiveTransactional(readOnly = true)
    public Mono<ArtistDTO> find(String id) {
        return existing(id).flatMap(this::toDto);
    }

    /** Sets the kind (null unclassifies). Conflicts when that would leave members without a Band or a Band as a member. */
    @ReactiveTransactional
    public Mono<ArtistDTO> setKind(String id, ArtistKind kind) {
        return existing(id)
                .flatMap(artist -> {
                    if (kind == artist.kind()) {
                        return Mono.just(artist);
                    }
                    var members = kind == ArtistKind.BAND ? Flux.<Artist>empty() : artists.members(id);
                    var bands = kind == ArtistKind.BAND ? artists.bands(id) : Flux.<Artist>empty();
                    return members.concatWith(bands).hasElements().flatMap(blocked -> blocked
                            ? Mono.<Artist>error(conflict("The artist has members or belongs to a band, so its kind cannot change that way."))
                            : artists.setKind(id, kind).thenReturn(new Artist(id, artist.name(), kind)));
                })
                .flatMap(this::toDto);
    }

    /** Replaces the members of a Band by the Artists of these names, created on first use and made Musicians. */
    @ReactiveTransactional
    public Mono<ArtistDTO> replaceMembers(String id, List<String> names) {
        return existing(id).flatMap(band -> {
            if (band.kind() != ArtistKind.BAND) {
                return Mono.error(conflict("Only a band has members."));
            }
            var distinct = new LinkedHashMap<String, String>();
            for (var name : names == null ? List.<String>of() : names) {
                if (name != null && !name.isBlank()) {
                    distinct.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), name.trim());
                }
            }
            if (distinct.containsKey(band.name().trim().toLowerCase(Locale.ROOT))) {
                return Mono.error(new BadRequestException("A band cannot be its own member."));
            }
            return Flux.fromIterable(distinct.values())
                    .concatMap(artists::findOrCreate)
                    .collectList()
                    .flatMap(members -> members.stream().anyMatch(member -> member.kind() == ArtistKind.BAND)
                            ? Mono.<Void>error(conflict("A band cannot be a member of a band."))
                            : artists.replaceMembers(id, members.stream().map(Artist::id).toList()))
                    .then(Mono.defer(() -> toDto(band)));
        });
    }

    private Mono<Artist> existing(String id) {
        return artists.find(id).switchIfEmpty(Mono.error(() -> new NotFoundException("No artist " + id)));
    }

    private Mono<ArtistDTO> toDto(Artist artist) {
        // Re-read the kind: it may have changed with the members
        return artists.find(artist.id()).flatMap(current -> {
            var members = current.kind() == ArtistKind.BAND ? refs(artists.members(current.id())) : Mono.just(List.<ArtistDTO.Ref>of());
            var bands = current.kind() == ArtistKind.MUSICIAN ? refs(artists.bands(current.id())) : Mono.just(List.<ArtistDTO.Ref>of());
            return Mono.zip(members, bands)
                    .map(both -> new ArtistDTO(current.id(), current.name(), current.kind(), both.getT1(), both.getT2()));
        });
    }

    private static Mono<List<ArtistDTO.Ref>> refs(Flux<Artist> artists) {
        return artists.map(artist -> new ArtistDTO.Ref(artist.id(), artist.name())).collectList();
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
