package org.tunelad.tune;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.tune.internal.TuneSource;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for openspec/specs/tune (the API): one test per spec scenario, named after it. */
@Import(TuneApiTest.StubSource.class)
class TuneApiTest extends AbstractApiTest {

    /** thesession.org knows tune 27, Cooley's, and fails for 500. */
    @TestConfiguration
    static class StubSource {
        @Bean
        @Primary
        TuneSource tuneSource() {
            return id -> switch (id) {
                case 27 -> Mono.just(new TuneSource.SourceTune(27, "Cooley's", List.of("Cooley's Reel"), null, DanceType.REEL, "Edorian",
                        "X:1\nT:Cooley's\nM:4/4\nL:1/8\nK:Edor\nEBBA B2 EB|"));
                case 500 -> Mono.error(new TuneSource.SourceUnavailableException("down", null));
                default -> Mono.empty();
            };
        }
    }

    @Autowired
    TuneSource source;

    // --- Requirement: Dance types ----------------------------------------------------------------

    @Test
    void list_dance_types() {
        var types = client.get().uri("/api/dance-types").exchange().expectStatus().isOk()
                .expectBodyList(DanceType.class).returnResult().getResponseBody();

        assertThat(types).contains(DanceType.JIG, DanceType.REEL, DanceType.HORNPIPE);
    }

    // --- Requirement: Create a Tune by hand ------------------------------------------------------

    @Test
    void create_a_tune() {
        var tune = create(Map.of("name", "The Kesh", "danceType", "jig", "aliases", List.of("Kesh Jig")), UPLOADER_TOKEN)
                .expectStatus().isCreated().expectBody(TuneDTO.class).returnResult().getResponseBody();

        assertThat(tune.name()).isEqualTo("The Kesh");
        assertThat(tune.danceType()).isEqualTo(DanceType.JIG);
        assertThat(tune.aliases()).containsExactly("Kesh Jig");
        assertThat(tune.sourceId()).isNull();
    }

    @Test
    void missing_name() {
        create(Map.of("danceType", "jig"), UPLOADER_TOKEN).expectStatus().isBadRequest();
    }

    @Test
    void unknown_dance_type() {
        create(Map.of("name", "The Kesh", "danceType", "foxtrot"), UPLOADER_TOKEN).expectStatus().isBadRequest();
        assertThat(count("tune_tunes", null)).isZero();
    }

    @Test
    void anonymous_create() {
        create(Map.of("name", "The Kesh", "danceType", "jig"), null).expectStatus().isUnauthorized();
    }

    // --- Requirement: Import a Tune from thesession.org ------------------------------------------

    @Test
    void import_a_tune() {
        var tune = importTune(27, UPLOADER_TOKEN).expectStatus().isCreated()
                .expectBody(TuneDTO.class).returnResult().getResponseBody();

        assertThat(tune.name()).isEqualTo("Cooley's");
        assertThat(tune.danceType()).isEqualTo(DanceType.REEL);
        assertThat(tune.sourceId()).isEqualTo(27);
        assertThat(tune.abc()).startsWith("X:1");
    }

    @Test
    void import_twice() {
        var first = importTune(27, UPLOADER_TOKEN).expectStatus().isCreated()
                .expectBody(TuneDTO.class).returnResult().getResponseBody();

        var second = importTune(27, TestTokensHolder.BOB).expectStatus().isOk()
                .expectBody(TuneDTO.class).returnResult().getResponseBody();

        assertThat(second).isEqualTo(first);
        assertThat(count("tune_tunes", null)).isOne();
    }

    @Test
    void unknown_thesession_id() {
        importTune(0, UPLOADER_TOKEN).expectStatus().isNotFound();
    }

    @Test
    void thesession_unreachable() {
        importTune(500, UPLOADER_TOKEN).expectStatus().isEqualTo(502);
    }

    // --- Requirement: Browse Tunes ---------------------------------------------------------------

    @Test
    void filter_by_dance_type() {
        create(Map.of("name", "The Kesh", "danceType", "jig"), UPLOADER_TOKEN).expectStatus().isCreated();
        importTune(27, UPLOADER_TOKEN).expectStatus().isCreated();

        var reels = list("danceType", "reel");

        assertThat(reels).extracting(TuneDTO::name).containsExactly("Cooley's");
    }

    @Test
    void find_by_alias() {
        create(Map.of("name", "The Kesh", "danceType", "jig", "aliases", List.of("Kesh Jig")), UPLOADER_TOKEN).expectStatus().isCreated();
        importTune(27, UPLOADER_TOKEN).expectStatus().isCreated();

        assertThat(list("q", "kesh jig")).extracting(TuneDTO::name).containsExactly("The Kesh");
        assertThat(list("q", "%")).isEmpty();
    }

    @Test
    void get_a_tune_and_an_unknown_one() {
        var kesh = create(Map.of("name", "The Kesh", "danceType", "jig"), UPLOADER_TOKEN)
                .expectBody(TuneDTO.class).returnResult().getResponseBody();

        assertThat(client.get().uri("/api/tunes/{id}", kesh.id()).exchange().expectStatus().isOk()
                .expectBody(TuneDTO.class).returnResult().getResponseBody()).isEqualTo(kesh);
        client.get().uri("/api/tunes/unknown").exchange().expectStatus().isNotFound();
    }

    // ---------------------------------------------------------------------------------------------

    WebTestClient.ResponseSpec create(Map<String, ?> body, String token) {
        return post("/api/tunes", body, token);
    }

    WebTestClient.ResponseSpec importTune(int id, String token) {
        return post("/api/tunes/import", Map.of("id", id), token);
    }

    private WebTestClient.ResponseSpec post(String uri, Map<String, ?> body, String token) {
        return client.post().uri(uri).headers(h -> {
            if (token != null) h.setBearerAuth(token);
        }).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    List<TuneDTO> list(String param, String value) {
        return client.get().uri(uri -> uri.path("/api/tunes").queryParam(param, value).build()).exchange().expectStatus().isOk()
                .expectBodyList(TuneDTO.class).returnResult().getResponseBody();
    }

    static class TestTokensHolder {
        static final String BOB = org.tunelad.TestTokens.bearer("bob", "Bob Smith");
    }
}
