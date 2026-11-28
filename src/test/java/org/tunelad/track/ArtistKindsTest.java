package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the Artist kinds of openspec/changes/band-and-musician: one test per spec scenario, named after it. */
class ArtistKindsTest extends AbstractApiTest {

    private String artist(String name) {
        uploadBy(name);
        return db.sql("SELECT id FROM track_artists WHERE lower(name) = lower(:n)").bind("n", name)
                .map(row -> row.get("id", String.class)).one().block();
    }

    private void uploadBy(String artist) {
        upload(Map.of("title", "Track by " + artist, "description", "A reel", "artist", artist), KNEES_UP_MP3)
                .expectStatus().isCreated();
    }

    private WebTestClient.ResponseSpec putJson(String uri, Object body, String token, Object... vars) {
        return client.put().uri(uri, vars).headers(h -> {
            if (token != null) h.setBearerAuth(token);
        }).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec setKind(String id, String kind) {
        var body = new java.util.HashMap<String, Object>();
        body.put("kind", kind);
        return putJson("/api/artists/{id}", body, UPLOADER_TOKEN, id);
    }

    private WebTestClient.ResponseSpec setMembers(String id, String... names) {
        return putJson("/api/artists/{id}/members", Map.of("members", List.of(names)), UPLOADER_TOKEN, id);
    }

    private ArtistDTO get(String id) {
        return client.get().uri("/api/artists/{id}", id).exchange().expectStatus().isOk()
                .expectBody(ArtistDTO.class).returnResult().getResponseBody();
    }

    private String band(String name) {
        var id = artist(name);
        setKind(id, "band").expectStatus().isOk();
        return id;
    }

    private static List<String> names(List<ArtistDTO.Ref> refs) {
        return refs.stream().map(ArtistDTO.Ref::name).toList();
    }

    // --- An Artist is a Musician, a Band or unclassified ---------------------------------------

    @Test
    void new_artists_are_unclassified() {
        assertThat(get(artist("Altan")).kind()).isNull();
    }

    @Test
    void classify_as_a_band() {
        var id = artist("Altan");

        var body = setKind(id, "band").expectStatus().isOk().expectBody(ArtistDTO.class).returnResult().getResponseBody();

        assertThat(body.kind()).isEqualTo(ArtistKind.BAND);
        assertThat(get(id).kind()).isEqualTo(ArtistKind.BAND);
    }

    @Test
    void unknown_artist() {
        setKind("no-such-artist", "band").expectStatus().isNotFound();
        client.get().uri("/api/artists/no-such-artist").exchange().expectStatus().isNotFound();
    }

    @Test
    void unauthenticated() {
        var id = artist("Altan");

        putJson("/api/artists/{id}", Map.of("kind", "band"), null, id).expectStatus().isUnauthorized();
        putJson("/api/artists/{id}/members", Map.of("members", List.of("A")), null, id).expectStatus().isUnauthorized();

        assertThat(get(id).kind()).isNull();
    }

    @Test
    void band_with_members_cannot_become_a_musician() {
        var id = band("Altan");
        setMembers(id, "Mairéad").expectStatus().isOk();

        setKind(id, "musician").expectStatus().isEqualTo(409);
        setKind(id, null).expectStatus().isEqualTo(409);

        assertThat(get(id).kind()).isEqualTo(ArtistKind.BAND);
    }

    @Test
    void band_without_members_can_be_unclassified() {
        var id = band("Altan");

        assertThat(setKind(id, null).expectStatus().isOk().expectBody(ArtistDTO.class).returnResult().getResponseBody().kind()).isNull();
    }

    // --- A Band has Musicians as members -------------------------------------------------------

    @Test
    void add_members_by_name() {
        var altan = band("Altan");

        setMembers(altan, "Mairéad Ní Mhaonaigh").expectStatus().isOk();

        var members = get(altan).members();
        assertThat(names(members)).containsExactly("Mairéad Ní Mhaonaigh");
        assertThat(get(members.getFirst().id()).kind()).isEqualTo(ArtistKind.MUSICIAN);
    }

    @Test
    void existing_unclassified_artist_becomes_a_musician() {
        var brock = artist("Paul Brock");
        var dervish = band("Dervish");

        setMembers(dervish, "  paul brock ").expectStatus().isOk();

        var members = get(dervish).members();
        assertThat(members).extracting(ArtistDTO.Ref::id).containsExactly(brock);
        assertThat(get(brock).kind()).isEqualTo(ArtistKind.MUSICIAN);
        assertThat(count("track_artists", "TRUE")).isEqualTo(2);
    }

    @Test
    void replacing_members() {
        var id = band("Altan");
        setMembers(id, "A", "B").expectStatus().isOk();
        var a = get(id).members().getFirst().id();

        setMembers(id, "B", "C").expectStatus().isOk();

        assertThat(names(get(id).members())).containsExactly("B", "C");
        assertThat(get(a).kind()).isEqualTo(ArtistKind.MUSICIAN);
    }

    @Test
    void blank_names_are_ignored_and_duplicates_count_once() {
        var id = band("Altan");

        setMembers(id, "A", " ", "a ", "").expectStatus().isOk();

        assertThat(names(get(id).members())).containsExactly("A");
    }

    @Test
    void a_band_cannot_be_a_member() {
        var altan = band("Altan");
        band("Dervish");
        setMembers(altan, "Mairéad").expectStatus().isOk();

        setMembers(altan, "Dervish").expectStatus().isEqualTo(409);

        assertThat(names(get(altan).members())).containsExactly("Mairéad");
    }

    @Test
    void a_band_cannot_be_its_own_member() {
        var altan = band("Altan");

        setMembers(altan, " altan").expectStatus().isBadRequest();
    }

    @Test
    void members_of_a_non_band() {
        var musician = artist("Paul Brock");
        setKind(musician, "musician").expectStatus().isOk();
        var unclassified = artist("Dervish");

        setMembers(musician, "A").expectStatus().isEqualTo(409);
        setMembers(unclassified, "A").expectStatus().isEqualTo(409);

        assertThat(count("track_band_members", null)).isZero();
    }

    @Test
    void a_musician_lists_its_bands() {
        var dervish = band("Dervish");
        setMembers(dervish, "Paul Brock").expectStatus().isOk();
        var brock = get(dervish).members().getFirst().id();

        assertThat(names(get(brock).bands())).containsExactly("Dervish");
        assertThat(get(brock).members()).isEmpty();
    }

    @Test
    void a_member_cannot_become_a_band() {
        var dervish = band("Dervish");
        setMembers(dervish, "Paul Brock").expectStatus().isOk();

        setKind(get(dervish).members().getFirst().id(), "band").expectStatus().isEqualTo(409);
    }

    // --- A Performance exposes its Artist ------------------------------------------------------

    @Test
    void performance_of_a_bands_track() {
        var kesh = createTune("The Kesh", "jig");
        var set = createSet(kesh);
        var altan = band("Altan");
        upload(Map.of("title", "Knees Up", "description", "A jig", "artist", "Altan", "setId", set.id()), KNEES_UP_MP3)
                .expectStatus().isCreated();
        upload(Map.of("title", "Mossy Banks", "description", "A jig", "setId", set.id()), MOSSY_BANKS_MP3)
                .expectStatus().isCreated();

        var performances = client.get().uri("/api/tunes/{id}/performances", kesh).exchange().expectStatus().isOk()
                .expectBodyList(TunePerformanceDTO.class).returnResult().getResponseBody();

        assertThat(performances).extracting(TunePerformanceDTO::trackTitle, TunePerformanceDTO::artist,
                        TunePerformanceDTO::artistId, TunePerformanceDTO::artistKind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Knees Up", "Altan", altan, ArtistKind.BAND),
                        org.assertj.core.groups.Tuple.tuple("Mossy Banks", null, null, null));
    }
}
