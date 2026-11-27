package org.tunelad.identification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tunelad.AbstractApiTest;
import org.tunelad.media.TestAudio;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.ApplyTags;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Characterization tests for the tags requirements of openspec/specs/identification: one test per scenario. */
class TagsApiTest extends AbstractApiTest {

    @Autowired
    TrackService tracks;

    private static final Map<String, String> ALL_TAGS = Map.of("title", "Rainfall", "artist", "Breabach",
            "album", "Live", "comment", "Rain on tin");

    private Map<String, String> noSet() {
        var parts = new HashMap<String, String>();
        parts.put("setId", null);
        return parts;
    }

    private TrackDTO uploadTagged(String file, byte[] audio, Map<String, String> parts) {
        return uploadFile(parts, named(file, audio)).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO getTrack(String id) {
        return client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO awaitTrack(String id, java.util.function.Predicate<TrackDTO> ready) {
        await().atMost(Duration.ofSeconds(20)).until(() -> ready.test(getTrack(id)));
        return getTrack(id);
    }

    @Test
    void tags_fill_an_upload_with_only_a_file() {
        var uploaded = uploadTagged("track01.mp3", TestAudio.tagged("mp3", ALL_TAGS), noSet());

        assertThat(uploaded.title()).isEqualTo("track01");
        var filled = awaitTrack(uploaded.id(), track -> "Rainfall".equals(track.title()));
        assertThat(filled.artist()).isEqualTo("Breabach");
        assertThat(filled.album()).isEqualTo("Live");
        assertThat(filled.description()).isEqualTo("Rain on tin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mp3", "m4a", "ogg", "flac", "wma"})
    void tags_of_each_format(String extension) {
        var uploaded = uploadTagged("track." + extension, TestAudio.tagged(extension, Map.of("title", "Tagged " + extension)),
                noSet());

        assertThat(awaitTrack(uploaded.id(), track -> track.title().startsWith("Tagged")).title())
                .isEqualTo("Tagged " + extension);
    }

    @Test
    void only_some_tags_present() {
        var uploaded = uploadTagged("reel.mp3", TestAudio.tagged("mp3", Map.of("artist", "Breabach")), noSet());

        var filled = awaitTrack(uploaded.id(), track -> track.artist() != null);
        assertThat(filled.title()).isEqualTo("reel");
        assertThat(filled.album()).isNull();
        assertThat(filled.description()).isNull();
    }

    @Test
    void the_artist_is_the_shared_record() {
        var first = uploadTagged("a.mp3", TestAudio.tagged("mp3", Map.of("artist", "Breabach")), noSet());
        awaitTrack(first.id(), track -> track.artist() != null);
        var second = uploadTagged("b.mp3", TestAudio.tagged("mp3", Map.of("artist", " breabach ")), noSet());
        awaitTrack(second.id(), track -> track.artist() != null);

        assertThat(count("track_artists", null)).isEqualTo(1);
    }

    @Test
    void blank_tags_are_ignored() {
        var uploaded = uploadTagged("reel.mp3", TestAudio.tagged("mp3", Map.of("title", " ", "album", " ", "artist", "Breabach")),
                noSet());

        var filled = awaitTrack(uploaded.id(), track -> track.artist() != null);
        assertThat(filled.title()).isEqualTo("reel");
        assertThat(filled.album()).isNull();
    }

    @Test
    void a_long_tag_title() {
        var uploaded = uploadTagged("reel.mp3", TestAudio.tagged("mp3", Map.of("title", "T".repeat(120))), noSet());

        assertThat(awaitTrack(uploaded.id(), track -> track.title().startsWith("T")).title()).isEqualTo("T".repeat(80));
    }

    @Test
    void the_uploaders_title_wins() {
        var parts = noSet();
        parts.put("title", "Wet Tuesday");
        var uploaded = uploadTagged("track.mp3", TestAudio.tagged("mp3", ALL_TAGS), parts);

        var filled = awaitTrack(uploaded.id(), track -> track.artist() != null);
        assertThat(filled.title()).isEqualTo("Wet Tuesday");
        assertThat(filled.album()).isEqualTo("Live");
        assertThat(filled.description()).isEqualTo("Rain on tin");
    }

    @Test
    void an_edit_before_the_tags_are_read() {
        var uploaded = uploadTagged("track.mp3", TestAudio.tagged("mp3", ALL_TAGS), noSet());
        edit(uploaded.id(), Map.of("title", "Mine"), UPLOADER_TOKEN).expectStatus().isOk();

        tracks.execute(new ApplyTags(uploaded.id(), "Rainfall", "Breabach", "Live", "Rain on tin")).block();

        var track = getTrack(uploaded.id());
        assertThat(track.title()).isEqualTo("Mine");
        assertThat(track.artist()).isNull();
        assertThat(track.album()).isNull();
        assertThat(track.description()).isNull();
    }

    @Test
    void an_edit_after_the_tags_were_read() {
        var uploaded = uploadTagged("track.mp3", TestAudio.tagged("mp3", ALL_TAGS), noSet());
        awaitTrack(uploaded.id(), track -> "Rainfall".equals(track.title()));
        edit(uploaded.id(), Map.of("title", "Mine", "artist", "Breabach", "album", "Live", "description", "Rain on tin"),
                UPLOADER_TOKEN).expectStatus().isOk();

        tracks.execute(new ApplyTags(uploaded.id(), "Rainfall", "Other", "Other", "Other")).block();

        assertThat(getTrack(uploaded.id()).title()).isEqualTo("Mine");
        assertThat(getTrack(uploaded.id()).artist()).isEqualTo("Breabach");
    }

    @Test
    void applying_tags_that_change_nothing_publishes_nothing() {
        var uploaded = uploadTagged("track.mp3", TestAudio.tagged("mp3", ALL_TAGS), noSet());
        awaitTrack(uploaded.id(), track -> "Rainfall".equals(track.title()));

        var again = tracks.execute(new ApplyTags(uploaded.id(), "Rainfall", "Breabach", "Live", "Rain on tin"));

        assertThat(again.blockOptional()).isEmpty();
    }

    @Test
    void applying_tags_to_a_deleted_track_does_nothing() {
        assertThat(tracks.execute(new ApplyTags("no-such-track", "T", null, null, null)).blockOptional()).isEmpty();
    }

    @Test
    void file_without_tags() {
        var uploaded = uploadTagged("reel.mp3", TestAudio.tagged("mp3", Map.of()), noSet());

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .until(() -> "reel".equals(getTrack(uploaded.id()).title()));
        assertThat(getTrack(uploaded.id()).artist()).isNull();
    }

    @Test
    void tags_cannot_be_read() {
        var uploaded = uploadTagged("reel.mp3", TestAudio.noise(), noSet());

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .until(() -> "reel".equals(getTrack(uploaded.id()).title()));
    }

    @Test
    void search_finds_the_track_by_its_tag_title() {
        var uploaded = uploadTagged("track01.mp3", TestAudio.tagged("mp3", ALL_TAGS), noSet());
        awaitTrack(uploaded.id(), track -> "Rainfall".equals(track.title()));

        await().atMost(Duration.ofSeconds(20)).until(() -> searchIds("rainfall").contains(uploaded.id()));
        await().atMost(Duration.ofSeconds(20)).until(() -> !searchIds("track01").contains(uploaded.id()));
    }

    private List<String> searchIds(String query) {
        return client.get().uri("/api/tracks/search?q={q}", query).exchange().expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody().stream().map(TrackDTO::id).toList();
    }
}
