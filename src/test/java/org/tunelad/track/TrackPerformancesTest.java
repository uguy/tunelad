package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.set.SetDTO;
import org.tunelad.tune.DanceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the Set and Performances of openspec/specs/track: one test per spec scenario, named after it. */
class TrackPerformancesTest extends AbstractApiTest {

    static final String BOB_TOKEN = TestTokens.bearer("bob", "Bob Smith");

    private String cooleys;
    private String kesh;
    private SetDTO set;

    private void twoTuneSet() {
        cooleys = createTune("Cooley's", "reel");
        kesh = createTune("The Kesh", "jig");
        set = createSet(cooleys, kesh);
    }

    private Map<String, String> parts(String performances) {
        var parts = new HashMap<String, String>();
        parts.put("title", "Knees Up");
        parts.put("description", "A reel and a jig");
        parts.put("setId", set.id());
        parts.put("performances", performances);
        return parts;
    }

    private TrackDTO uploadOk(String performances) {
        return upload(parts(performances), KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO get(String id) {
        return client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    // --- Upload ------------------------------------------------------------------------------

    @Test
    void upload_records_the_set() {
        twoTuneSet();

        var track = uploadOk(null);

        assertThat(track.set()).isEqualTo(set);
        assertThat(get(track.id()).set()).isEqualTo(set);
    }

    @Test
    void performances_default() {
        twoTuneSet();

        var track = uploadOk(null);

        assertThat(track.performances()).containsExactly(
                new PerformanceDTO(1, cooleys, "Cooley's", DanceType.REEL, 0, null, null, null, IdentificationState.PENDING),
                new PerformanceDTO(2, kesh, "The Kesh", DanceType.JIG, 0, null, null, null, IdentificationState.PENDING));
    }

    @Test
    void performances_given() {
        twoTuneSet();

        var track = uploadOk("""
                [{"startOffsetSeconds": 0, "key": "Em"},
                 {"startOffsetSeconds": 95.5, "key": "G", "notes": "plays the B part twice"}]""");

        assertThat(get(track.id()).performances()).containsExactly(
                new PerformanceDTO(1, cooleys, "Cooley's", DanceType.REEL, 0, "Em", null, null, IdentificationState.PENDING),
                new PerformanceDTO(2, kesh, "The Kesh", DanceType.JIG, 95.5, "G", "plays the B part twice", null, IdentificationState.PENDING));
    }

    @Test
    void setId_missing() {
        twoTuneSet();
        var parts = parts(null);
        parts.put("setId", null);

        upload(parts, KNEES_UP_MP3).expectStatus().isBadRequest();

        assertThat(count("track_tracks", null)).isZero();
    }

    @Test
    void unknown_set() {
        twoTuneSet();
        var parts = parts(null);
        parts.put("setId", "no-such-set");

        upload(parts, KNEES_UP_MP3).expectStatus().isBadRequest();

        assertThat(count("track_tracks", null)).isZero();
        assertThat(count("media_files", null)).isZero();
    }

    @Test
    void invalid_performances() {
        twoTuneSet();
        var tooLongKey = "k".repeat(31);
        var tooLongNotes = "n".repeat(2001);

        for (var performances : List.of(
                "[{}]", "[{}, {}, {}]", "[{}, {\"startOffsetSeconds\": -1}]",
                "[{}, {\"key\": \"" + tooLongKey + "\"}]", "[{}, {\"notes\": \"" + tooLongNotes + "\"}]",
                "{\"key\": \"Em\"}", "not json")) {
            upload(parts(performances), KNEES_UP_MP3).expectStatus().isBadRequest();
        }

        assertThat(count("track_tracks", null)).isZero();
        assertThat(count("media_files", null)).isZero();
    }

    // --- A track shows its Set and Performances --------------------------------------------------

    @Test
    void track_from_before_sets() {
        db.sql("INSERT INTO track_tracks (id, version, title, description, format, owner_id, owner_display_name) "
                        + "VALUES ('old', 0, 'Old', 'Before sets', 'MP3', 'account-alice', 'Alice Liddell')")
                .then().block();

        var track = get("old");

        assertThat(track.set()).isNull();
        assertThat(track.performances()).isEmpty();
        assertThat(client.get().uri("/api/tracks").exchange().expectBodyList(TrackDTO.class).returnResult().getResponseBody())
                .extracting(TrackDTO::id).containsExactly("old");
    }

    @Test
    void tune_renamed() {
        twoTuneSet();
        var track = uploadOk(null);

        db.sql("UPDATE tune_tunes SET name = 'Cooley''s Reel' WHERE id = :id").bind("id", cooleys).then().block();

        assertThat(get(track.id()).performances()).extracting(PerformanceDTO::tuneName)
                .containsExactly("Cooley's Reel", "The Kesh");
    }

    // --- Edit a track's Performances -----------------------------------------------------------

    private TrackDTO edit(TrackDTO track, Object performances) {
        var body = new HashMap<String, Object>(Map.of("title", track.title(), "description", track.description()));
        if (performances != null) body.put("performances", performances);
        return edit(track.id(), body, UPLOADER_TOKEN).expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    @Test
    void owner_edits_a_performance() {
        twoTuneSet();
        var track = uploadOk(null);

        var updated = edit(track, List.of(Map.of(), Map.of("key", "Am", "startOffsetSeconds", 120)));

        assertThat(updated.performances().get(1).key()).isEqualTo("Am");
        assertThat(updated.performances().get(1).startOffsetSeconds()).isEqualTo(120);
        assertThat(get(track.id()).performances()).isEqualTo(updated.performances());
        assertThat(updated.set()).isEqualTo(set);
        assertThat(updated.performances()).extracting(PerformanceDTO::tuneId).containsExactly(cooleys, kesh);
    }

    @Test
    void performances_omitted() {
        twoTuneSet();
        var track = uploadOk("[{\"key\": \"Em\"}, {}]");

        var updated = edit(track, null);

        assertThat(updated.performances().get(0).key()).isEqualTo("Em");
    }

    @Test
    void wrong_number_of_performances_or_invalid_values() {
        twoTuneSet();
        var track = uploadOk("[{\"key\": \"Em\"}, {}]");

        for (var performances : List.of(List.of(Map.of()), List.of(Map.of(), Map.of(), Map.of()),
                List.of(Map.of(), Map.of("startOffsetSeconds", -1)), List.of(Map.of(), Map.of("key", "k".repeat(31))),
                List.of(Map.of(), Map.of("notes", "n".repeat(2001))))) {
            edit(track.id(), Map.of("title", "T", "description", "D", "performances", performances), UPLOADER_TOKEN)
                    .expectStatus().isBadRequest();
        }

        assertThat(get(track.id()).performances().get(0).key()).isEqualTo("Em");
        assertThat(get(track.id()).title()).isEqualTo("Knees Up");
    }

    // --- A Performance carries a Transcription ----------------------------------------------------

    @Test
    void write_a_transcription() {
        twoTuneSet();
        var track = uploadOk(null);

        var updated = edit(track, List.of(Map.of(), Map.of("abc", "X:1\nK:G\nGABc|")));

        assertThat(updated.performances().get(1).abc()).isEqualTo("X:1\nK:G\nGABc|");
        assertThat(updated.performances().get(0).abc()).isNull();
        assertThat(get(track.id()).performances().get(1).abc()).isEqualTo("X:1\nK:G\nGABc|");
    }

    @Test
    void clear_a_transcription() {
        twoTuneSet();
        var track = uploadOk("[{\"abc\": \"X:1\\nK:G\\nGABc|\"}, {}]");
        assertThat(track.performances().get(0).abc()).isNotNull();

        var updated = edit(track, List.of(Map.of(), Map.of()));

        assertThat(updated.performances().get(0).abc()).isNull();
        assertThat(get(track.id()).performances().get(0).abc()).isNull();
    }

    @Test
    void transcription_too_long() {
        twoTuneSet();
        var track = uploadOk("[{\"abc\": \"X:1\"}, {}]");

        edit(track.id(), Map.of("title", "T", "description", "D",
                "performances", List.of(Map.of("abc", "a".repeat(20_001)), Map.of())), UPLOADER_TOKEN)
                .expectStatus().isBadRequest();

        assertThat(get(track.id()).performances().get(0).abc()).isEqualTo("X:1");
    }

    @Test
    void not_the_owner() {
        twoTuneSet();
        var track = uploadOk(null);

        edit(track.id(), Map.of("title", "T", "description", "D", "performances", List.of(Map.of("key", "Am"), Map.of())), BOB_TOKEN)
                .expectStatus().isForbidden();

        assertThat(get(track.id()).performances().get(0).key()).isNull();
    }

    // --- Performances of a Tune ------------------------------------------------------------------

    private List<TunePerformanceDTO> performancesOf(String tuneId) {
        return client.get().uri("/api/tunes/{id}/performances", tuneId).exchange().expectStatus().isOk()
                .expectBodyList(TunePerformanceDTO.class).returnResult().getResponseBody();
    }

    @Test
    void performances_across_tracks() {
        twoTuneSet();
        uploadOk("[{}, {\"key\": \"G\", \"abc\": \"X:1\"}]");
        var parts = parts(null);
        parts.put("title", "Another go");
        upload(parts, KNEES_UP_MP3).expectStatus().isCreated();

        var performances = performancesOf(kesh);

        assertThat(performances).extracting(TunePerformanceDTO::trackTitle).containsExactly("Another go", "Knees Up");
        assertThat(performances).extracting(TunePerformanceDTO::position).containsOnly(2);
        assertThat(performances.get(1).key()).isEqualTo("G");
        assertThat(performances.get(1).abc()).isEqualTo("X:1");
        assertThat(performancesOf(cooleys)).hasSize(2);
    }

    @Test
    void tune_never_recorded() {
        twoTuneSet();

        assertThat(performancesOf(kesh)).isEmpty();
    }

    // --- Deleting a track deletes its Performances -----------------------------------------------

    @Test
    void delete_a_track_with_performances() {
        twoTuneSet();
        var track = uploadOk(null);
        assertThat(count("track_performances", null)).isEqualTo(2);

        client.delete().uri("/api/tracks/{id}", track.id()).headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .exchange().expectStatus().isNoContent();

        assertThat(count("track_performances", null)).isZero();
        assertThat(count("set_sets", null)).isOne();
        assertThat(count("tune_tunes", null)).isEqualTo(2);
    }
}
