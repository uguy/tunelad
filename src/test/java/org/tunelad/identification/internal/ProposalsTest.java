package org.tunelad.identification.internal;

import org.junit.jupiter.api.Test;
import org.tunelad.identification.DetectedTune;
import org.tunelad.track.IdentificationState;
import org.tunelad.track.PerformanceDTO;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.command.ProposePerformances.Proposal;
import org.tunelad.tune.DanceType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** How detections become proposals, without a database or a sidecar. */
class ProposalsTest {

    private static TrackDTO trackOf(String... tuneIds) {
        var performances = new java.util.ArrayList<PerformanceDTO>();
        for (int i = 0; i < tuneIds.length; i++) {
            performances.add(new PerformanceDTO(i + 1, tuneIds[i], tuneIds[i], DanceType.REEL, 0, null, null, null, IdentificationState.PENDING));
        }
        return new TrackDTO("t", "title", null, null, "d", null, null, null, performances);
    }

    private static DetectedTune heard(String sourceId, double start) {
        return new DetectedTune(sourceId, start, start + 60, 0.6);
    }

    @Test
    void a_tune_played_twice_takes_its_detections_in_time_order() {
        var proposals = IdentificationService.proposals(trackOf("a", "b", "a"), Map.of("a", "1", "b", "2"),
                List.of(heard("1", 5), heard("2", 70), heard("1", 130)));

        assertThat(proposals).containsExactly(new Proposal(1, 5), new Proposal(2, 70), new Proposal(3, 130));
    }

    @Test
    void a_tune_heard_once_but_played_twice_fills_the_first_performance_only() {
        var proposals = IdentificationService.proposals(trackOf("a", "a"), Map.of("a", "1"), List.of(heard("1", 5)));

        assertThat(proposals).containsExactly(new Proposal(1, 5));
    }

    @Test
    void a_tune_without_a_source_id_or_a_detection_gets_no_proposal() {
        var proposals = IdentificationService.proposals(trackOf("a", "b", "c"), Map.of("a", "1", "b", "2"), List.of(heard("1", 5)));

        assertThat(proposals).containsExactly(new Proposal(1, 5));
    }
}
