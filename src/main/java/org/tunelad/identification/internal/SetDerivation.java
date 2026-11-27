package org.tunelad.identification.internal;

import org.tunelad.identification.DetectedTune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Decides, from what an open-ended analysis heard, the ordered Tunes of a Track's Set. */
final class SetDerivation {

    /** The most entries kept: the limit of a Set. */
    static final int MAX_ENTRIES = 20;

    /** A Tune of the derived Set: its thesession.org id and where it was first heard, in seconds. */
    record Entry(String sourceId, double start) {
    }

    private SetDerivation() {
    }

    /**
     * Drops detections under {@code minConfidence}, merges consecutive detections of one Tune keeping the first start,
     * keeps a Tune heard again after another as a further entry, and keeps the first {@link #MAX_ENTRIES}.
     */
    static List<Entry> derive(List<DetectedTune> heard, double minConfidence) {
        var entries = new ArrayList<Entry>();
        for (var detected : heard.stream().filter(d -> d.confidence() >= minConfidence)
                .sorted(Comparator.comparingDouble(DetectedTune::start)).toList()) {
            if (entries.isEmpty() || !entries.getLast().sourceId().equals(detected.tuneId())) {
                entries.add(new Entry(detected.tuneId(), detected.start()));
            }
        }
        return List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
    }
}
