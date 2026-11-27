package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.tunelad.AbstractApiTest;
import org.tunelad.set.SetDTO;
import org.tunelad.track.command.ProposePerformances;
import org.tunelad.track.command.ProposePerformances.Proposal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the Identification state of Performances: openspec/specs/track and the proposal rules of openspec/specs/identification. */
class PerformanceIdentificationTest extends AbstractApiTest {

    @Autowired
    private TrackService tracks;

    private SetDTO set;

    private TrackDTO upload() {
        set = createSet(createTune("Cooley's", "reel"), createTune("The Kesh", "jig"));
        var parts = new HashMap<String, String>();
        parts.put("title", "Knees Up");
        parts.put("description", "A reel and a jig");
        parts.put("setId", set.id());
        return upload(parts, KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO get(String id) {
        return client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO edit(TrackDTO track, List<Map<String, Object>> performances) {
        return edit(track.id(), Map.of("title", track.title(), "description", track.description(), "performances", performances),
                UPLOADER_TOKEN).expectStatus().isOk().expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private static List<IdentificationState> states(TrackDTO track) {
        return track.performances().stream().map(PerformanceDTO::identification).toList();
    }

    @Test
    void new_track_is_pending() {
        var track = upload();

        assertThat(states(track)).containsExactly(IdentificationState.PENDING, IdentificationState.PENDING);
        assertThat(states(get(track.id()))).containsExactly(IdentificationState.PENDING, IdentificationState.PENDING);
    }

    @Test
    void performances_from_before_identification_are_corrected() {
        var track = upload();
        db.sql("DELETE FROM track_performances WHERE track_id = :id").bind("id", track.id()).then().block();
        db.sql("INSERT INTO track_performances (track_id, position, tune_id) VALUES (:id, 1, :tune)")
                .bind("id", track.id()).bind("tune", track.performances().get(0).tuneId()).then().block();

        assertThat(states(get(track.id()))).containsExactly(IdentificationState.CORRECTED);
    }

    @Test
    void key_edited_marks_corrected() {
        var track = upload();
        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 95.5)))).block();

        var updated = edit(track, List.of(Map.of(), Map.of("startOffsetSeconds", 95.5, "key", "Em")));

        assertThat(states(updated)).containsExactly(IdentificationState.PENDING, IdentificationState.CORRECTED);
        assertThat(states(get(track.id()))).containsExactly(IdentificationState.PENDING, IdentificationState.CORRECTED);
    }

    @Test
    void saved_unchanged_keeps_the_state() {
        var track = upload();
        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 95.5)))).block();

        var updated = edit(track, List.of(Map.of(), Map.of("startOffsetSeconds", 95.5)));

        assertThat(states(updated)).containsExactly(IdentificationState.PENDING, IdentificationState.IDENTIFIED);
    }

    @Test
    void states_are_lowercase_words_in_the_json() {
        var track = upload();
        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 95.5)), Set.of(1, 2))).block();

        client.get().uri("/api/tracks/{id}", track.id()).exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.performances[0].identification").isEqualTo("nothing_found")
                .jsonPath("$.performances[1].identification").isEqualTo("identified");
        client.get().uri("/api/tunes/{id}/performances", track.performances().get(1).tuneId()).exchange()
                .expectBody().jsonPath("$[0].identification").isEqualTo("identified");
    }

    @Test
    void nothing_found_for_the_performances_left_pending() {
        var track = upload();

        var updated = tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 95.5)), Set.of(1, 2))).block();

        assertThat(updated).isNotNull();
        assertThat(states(updated)).containsExactly(IdentificationState.NOTHING_FOUND, IdentificationState.IDENTIFIED);
        assertThat(states(get(track.id()))).containsExactly(IdentificationState.NOTHING_FOUND, IdentificationState.IDENTIFIED);
    }

    @Test
    void nothing_found_leaves_identified_and_corrected_performances_alone() {
        var track = upload();
        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(1, 3)))).block();
        edit(track, List.of(Map.of("startOffsetSeconds", 3), Map.of("key", "Em")));
        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(1, 4)))).block();

        var result = tracks.execute(new ProposePerformances(track.id(), List.of(), Set.of(1, 2))).block();

        assertThat(result).isNull();
        // Performance 1 stays identified (the edit changed nothing in it), 2 stays corrected
        assertThat(states(get(track.id()))).containsExactly(IdentificationState.IDENTIFIED, IdentificationState.CORRECTED);
    }

    @Test
    void saving_a_nothing_found_performance_unchanged_keeps_the_state() {
        var track = upload();
        tracks.execute(new ProposePerformances(track.id(), List.of(), Set.of(1, 2))).block();

        var updated = edit(track, List.of(Map.of(), Map.of()));

        assertThat(states(updated)).containsOnly(IdentificationState.NOTHING_FOUND);
    }

    @Test
    void a_proposal_after_nothing_found_identifies() {
        var track = upload();
        tracks.execute(new ProposePerformances(track.id(), List.of(), Set.of(1, 2))).block();

        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(1, 3)))).block();

        assertThat(states(get(track.id()))).containsExactly(IdentificationState.IDENTIFIED, IdentificationState.NOTHING_FOUND);
    }

    @Test
    void proposal_fills_a_pending_performance() {
        var track = upload();

        var updated = tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 95.5)))).block();

        assertThat(updated).isNotNull();
        assertThat(states(updated)).containsExactly(IdentificationState.PENDING, IdentificationState.IDENTIFIED);
        assertThat(updated.performances().get(1).startOffsetSeconds()).isEqualTo(95.5);
        assertThat(updated.performances().get(0).startOffsetSeconds()).isZero();
        assertThat(get(track.id()).performances().get(1).startOffsetSeconds()).isEqualTo(95.5);
    }

    @Test
    void a_later_proposal_replaces_an_identified_one() {
        var track = upload();
        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 95.5)))).block();

        tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 100)))).block();

        assertThat(get(track.id()).performances().get(1).startOffsetSeconds()).isEqualTo(100);
    }

    @Test
    void proposal_never_overwrites_a_correction() {
        var track = upload();
        edit(track, List.of(Map.of(), Map.of("startOffsetSeconds", 90)));

        var result = tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(2, 95.5)))).block();

        assertThat(result).isNull();
        var stored = get(track.id()).performances().get(1);
        assertThat(stored.startOffsetSeconds()).isEqualTo(90);
        assertThat(stored.identification()).isEqualTo(IdentificationState.CORRECTED);
    }

    @Test
    void unknown_positions_and_tracks_are_ignored() {
        var track = upload();

        assertThat(tracks.execute(new ProposePerformances(track.id(), List.of(new Proposal(5, 10)))).block()).isNull();
        assertThat(tracks.execute(new ProposePerformances("no-such-track", List.of(new Proposal(1, 10)))).block()).isNull();

        assertThat(states(get(track.id()))).containsOnly(IdentificationState.PENDING);
    }
}
