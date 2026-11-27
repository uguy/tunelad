package org.tunelad.identification.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tunelad.identification.DetectedTune;
import org.tunelad.identification.IdentificationSource;
import org.tunelad.media.MediaStore;
import org.tunelad.track.PerformanceDTO;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.ProposePerformances;
import org.tunelad.track.command.ProposePerformances.Proposal;
import org.tunelad.tune.TuneCatalog;
import org.tunelad.tune.TuneDTO;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Identifies a Track: hears its audio and proposes the start of each Performance whose Tune was heard. */
@Slf4j
@Service
public class IdentificationService {

    private final TrackService tracks;

    private final TuneCatalog tunes;

    private final MediaStore media;

    private final IdentificationSource source;

    IdentificationService(TrackService tracks, TuneCatalog tunes, MediaStore media, IdentificationSource source) {
        this.tracks = tracks;
        this.tunes = tunes;
        this.media = media;
        this.source = source;
    }

    /**
     * Proposes the Performances of the Track with this id, then says "nothing found" for those still pending whose
     * Tune was looked for. Completes empty, having changed nothing, when the track is gone, has no Set, or none of its
     * Tunes comes from thesession.org; the Performances an Account corrected are never changed. Fails, leaving everything as it was, when the analysis
     * cannot be done.
     */
    public Mono<Void> identify(String trackId) {
        return tracks.findById(trackId)
                .filter(track -> track.set() != null && !track.performances().isEmpty())
                .flatMap(this::propose);
    }

    private Mono<Void> propose(TrackDTO track) {
        var tuneIds = track.performances().stream().map(PerformanceDTO::tuneId).distinct().toList();
        return tunes.findAllById(tuneIds)
                .filter(tune -> tune.sourceId() != null)
                .collectMap(TuneDTO::id, tune -> String.valueOf(tune.sourceId()))
                // None of the Tunes can be recognised: nothing is asked, and nothing changes
                .filter(sourceIds -> !sourceIds.isEmpty())
                .flatMap(sourceIds -> source.detect(media.read(track.id()), sourceIds.values().stream().distinct().toList())
                        .collectList()
                        .map(heard -> new ProposePerformances(track.id(), proposals(track, sourceIds, heard), searched(track, sourceIds))))
                .flatMap(tracks::execute)
                .doOnNext(updated -> log.info("Track {} identified", updated.id()))
                .then();
    }

    /** The positions of the Performances whose Tune was looked for: those with a thesession.org id. */
    static Set<Integer> searched(TrackDTO track, Map<String, String> sourceIds) {
        return track.performances().stream().filter(p -> sourceIds.containsKey(p.tuneId()))
                .map(PerformanceDTO::position).collect(Collectors.toSet());
    }

    /**
     * One proposal per Performance whose Tune was heard. A Tune played several times in the Set takes the detections
     * in time order, the first with the first Performance of that Tune, and so on.
     */
    static List<Proposal> proposals(TrackDTO track, Map<String, String> sourceIds, List<DetectedTune> heard) {
        var byTune = heard.stream().collect(Collectors.groupingBy(DetectedTune::tuneId));
        var used = new HashMap<String, Integer>();
        var proposals = new ArrayList<Proposal>();
        for (var performance : track.performances()) {
            var sourceId = sourceIds.get(performance.tuneId());
            if (sourceId == null) {
                continue;
            }
            var detections = byTune.getOrDefault(sourceId, List.of());
            var n = used.merge(sourceId, 1, Integer::sum) - 1;
            if (n < detections.size()) {
                proposals.add(new Proposal(performance.position(), detections.get(n).start()));
            }
        }
        return List.copyOf(proposals);
    }
}
