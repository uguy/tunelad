package org.tunelad.track.internal.db;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.tunelad.set.SetCatalog;
import org.tunelad.set.SetDTO;
import org.tunelad.track.TunePerformanceDTO;
import org.tunelad.track.internal.Artist;
import org.tunelad.track.internal.Artists;
import org.tunelad.track.internal.Performance;
import org.tunelad.track.internal.Track;
import org.tunelad.track.internal.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Component
class DbTracks implements Tracks {

    private final R2dbcTrackRepository trackRepository;

    private final Artists artists;

    private final SetCatalog sets;

    private final DatabaseClient client;

    DbTracks(R2dbcTrackRepository trackRepository, Artists artists, SetCatalog sets, DatabaseClient client) {
        this.trackRepository = trackRepository;
        this.artists = artists;
        this.sets = sets;
        this.client = client;
    }

    @Override
    public Flux<Track> findAll() {
        return hydrate(trackRepository.findAll());
    }

    @Override
    public Flux<Track> findAllById(java.util.Collection<String> ids) {
        return ids.isEmpty() ? Flux.empty() : hydrate(trackRepository.findAllById(ids));
    }

    @Override
    public Flux<TunePerformanceDTO> findPerformancesByTune(String tuneId) {
        return client.sql("""
                        SELECT t.id AS track_id, t.title, a.name AS artist, p.position, p.start_offset_seconds,
                               p.key, p.notes, p.abc, p.identification
                        FROM track_performances p
                                 JOIN track_tracks t ON t.id = p.track_id
                                 LEFT JOIN track_artists a ON a.id = t.artist_id
                        WHERE p.tune_id = :tune
                        ORDER BY lower(t.title), t.id, p.position""")
                .bind("tune", tuneId)
                .map(row -> new TunePerformanceDTO(row.get("track_id", String.class), row.get("title", String.class),
                        row.get("artist", String.class), row.get("position", Integer.class),
                        row.get("start_offset_seconds", BigDecimal.class).doubleValue(),
                        row.get("key", String.class), row.get("notes", String.class), row.get("abc", String.class),
                        org.tunelad.track.IdentificationState.fromColumn(row.get("identification", String.class))))
                .all();
    }

    @Override
    public Mono<Track> save(Track track) {
        if (track.getId() == null || track.getId().isBlank()) {
            track.setId(UUID.randomUUID().toString());
        }
        var artist = track.getArtist();
        var set = track.getTuneSet();
        var setId = track.getSetId();
        var performances = track.getPerformances();
        var provided = track.getProvidedTextFields();
        track.setArtistId(artist == null ? null : artist.id());
        return trackRepository.save(track)
                .flatMap(saved -> storeSetId(saved.getId(), setId)
                        .then(storeProvidedFields(saved.getId(), provided))
                        .then(replacePerformances(saved.getId(), performances)).thenReturn(saved))
                .doOnNext(saved -> {
                    saved.setSetId(setId);
                    saved.setArtist(artist);
                    saved.setTuneSet(set);
                    saved.setPerformances(performances);
                    saved.setProvidedTextFields(provided);
                });
    }

    @Override
    public Mono<Track> findById(String id) {
        return hydrate(trackRepository.findById(id).flux()).next();
    }

    @Override
    public Mono<Track> findByIdForUpdate(String id) {
        return client.sql("SELECT id FROM track_tracks WHERE id = :id FOR UPDATE").bind("id", id).fetch().first()
                .then(Mono.defer(() -> findById(id)));
    }

    @Override
    public Mono<Void> savePerformances(Track track) {
        return replacePerformances(track.getId(), track.getPerformances());
    }

    @Override
    public Mono<Void> delete(Track track) {
        // its Performances go with it: ON DELETE CASCADE
        return trackRepository.delete(track);
    }

    private Mono<Void> storeSetId(String trackId, String setId) {
        var spec = client.sql("UPDATE track_tracks SET set_id = :set WHERE id = :track").bind("track", trackId);
        return (setId == null ? spec.bindNull("set", String.class) : spec.bind("set", setId)).then();
    }

    private Mono<Void> storeProvidedFields(String trackId, List<String> provided) {
        return client.sql("UPDATE track_tracks SET provided_text_fields = :fields WHERE id = :track")
                .bind("track", trackId).bind("fields", provided.toArray(new String[0])).then();
    }

    private Mono<Map<String, List<String>>> providedFieldsOf(List<String> trackIds) {
        return client.sql("SELECT id, provided_text_fields FROM track_tracks WHERE id IN (:ids)")
                .bind("ids", trackIds)
                .map(row -> Map.entry(row.get("id", String.class),
                        (List<String>) new ArrayList<>(List.of(row.get("provided_text_fields", String[].class)))))
                .all().collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<Map<String, String>> setIdsOf(List<String> trackIds) {
        return client.sql("SELECT id, set_id FROM track_tracks WHERE id IN (:ids) AND set_id IS NOT NULL")
                .bind("ids", trackIds)
                .map(row -> Map.entry(row.get("id", String.class), row.get("set_id", String.class)))
                .all().collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<Void> replacePerformances(String trackId, List<Performance> performances) {
        return client.sql("DELETE FROM track_performances WHERE track_id = :track").bind("track", trackId).then()
                .thenMany(Flux.fromIterable(performances).concatMap(p -> {
                    var spec = client.sql("""
                                    INSERT INTO track_performances (track_id, position, tune_id, start_offset_seconds, key, notes, abc, identification)
                                    VALUES (:track, :position, :tune, :offset, :key, :notes, :abc, :identification)""")
                            .bind("track", trackId).bind("position", p.position()).bind("tune", p.tuneId())
                            .bind("offset", BigDecimal.valueOf(p.startOffsetSeconds()))
                            .bind("identification", p.identification().column());
                    spec = p.key() == null ? spec.bindNull("key", String.class) : spec.bind("key", p.key());
                    spec = p.notes() == null ? spec.bindNull("notes", String.class) : spec.bind("notes", p.notes());
                    spec = p.abc() == null ? spec.bindNull("abc", String.class) : spec.bind("abc", p.abc());
                    return spec.then();
                }))
                .then();
    }

    /** Loads the Artists, Sets and Performances of all the tracks with one query each, rather than one per track. */
    private Flux<Track> hydrate(Flux<Track> found) {
        return found.collectList().flatMapMany(list -> {
            if (list.isEmpty()) {
                return Flux.empty();
            }
            var artistIds = list.stream().map(Track::getArtistId).filter(Objects::nonNull).distinct().toList();
            var trackIds = list.stream().map(Track::getId).toList();
            return setIdsOf(trackIds).flatMap(setIdByTrack -> Mono.zip(
                            artists.findAllById(artistIds).collectMap(Artist::id, Function.identity()),
                            sets.findAllById(setIdByTrack.values().stream().distinct().toList())
                                    .collectMap(SetDTO::id, Function.identity()),
                            performancesOf(trackIds), providedFieldsOf(trackIds))
                    .map(loaded -> {
                        list.forEach(track -> {
                            track.setArtist(track.getArtistId() == null ? null : loaded.getT1().get(track.getArtistId()));
                            track.setSetId(setIdByTrack.get(track.getId()));
                            track.setTuneSet(track.getSetId() == null ? null : loaded.getT2().get(track.getSetId()));
                            track.setPerformances(loaded.getT3().getOrDefault(track.getId(), new ArrayList<>()));
                            track.setProvidedTextFields(loaded.getT4().getOrDefault(track.getId(), new ArrayList<>()));
                        });
                        return list;
                    })).flatMapMany(Flux::fromIterable);
        });
    }

    private Mono<Map<String, List<Performance>>> performancesOf(List<String> trackIds) {
        if (trackIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return client.sql("""
                        SELECT track_id, position, tune_id, start_offset_seconds, key, notes, abc, identification
                        FROM track_performances WHERE track_id IN (:ids) ORDER BY track_id, position""")
                .bind("ids", trackIds)
                .map(row -> Map.entry(row.get("track_id", String.class), new Performance(
                        row.get("position", Integer.class), row.get("tune_id", String.class),
                        row.get("start_offset_seconds", BigDecimal.class).doubleValue(),
                        row.get("key", String.class), row.get("notes", String.class), row.get("abc", String.class),
                        org.tunelad.track.IdentificationState.fromColumn(row.get("identification", String.class)))))
                .all()
                .collect(HashMap::new, (map, entry) ->
                        map.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue()));
    }
}
