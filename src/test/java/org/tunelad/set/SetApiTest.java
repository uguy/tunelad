package org.tunelad.set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.tune.TuneDTO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for openspec/specs/set (the API): one test per spec scenario, named after it. */
class SetApiTest extends AbstractApiTest {

    static final String BOB_TOKEN = TestTokens.bearer("bob", "Bob Smith");

    // --- Requirement: One Set per ordered list of Tunes -------------------------------------------

    @Test
    void create_a_set() {
        var cooleys = tune("Cooley's", "reel");
        var kesh = tune("The Kesh", "jig");

        var set = create(List.of(cooleys, kesh), UPLOADER_TOKEN).expectStatus().isCreated()
                .expectBody(SetDTO.class).returnResult().getResponseBody();

        assertThat(set.tunes()).extracting(SetDTO.Entry::name).containsExactly("Cooley's", "The Kesh");
    }

    @Test
    void the_same_list_gives_the_same_set() {
        var cooleys = tune("Cooley's", "reel");
        var kesh = tune("The Kesh", "jig");
        var first = create(List.of(cooleys, kesh), UPLOADER_TOKEN).expectStatus().isCreated()
                .expectBody(SetDTO.class).returnResult().getResponseBody();

        var second = create(List.of(cooleys, kesh), BOB_TOKEN).expectStatus().isOk()
                .expectBody(SetDTO.class).returnResult().getResponseBody();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(count("set_sets", null)).isOne();
    }

    @Test
    void another_order_is_another_set() {
        var cooleys = tune("Cooley's", "reel");
        var kesh = tune("The Kesh", "jig");
        create(List.of(cooleys, kesh), UPLOADER_TOKEN).expectStatus().isCreated();

        create(List.of(kesh, cooleys), UPLOADER_TOKEN).expectStatus().isCreated();

        assertThat(count("set_sets", null)).isEqualTo(2);
    }

    @Test
    void a_set_of_one_tune() {
        create(List.of(tune("Cooley's", "reel")), UPLOADER_TOKEN).expectStatus().isCreated();
    }

    @Test
    void a_tune_may_repeat() {
        var cooleys = tune("Cooley's", "reel");

        var set = create(List.of(cooleys, cooleys), UPLOADER_TOKEN).expectStatus().isCreated()
                .expectBody(SetDTO.class).returnResult().getResponseBody();

        assertThat(set.tunes()).hasSize(2);
    }

    @Test
    void identical_creates_at_once_end_with_one_set() {
        var list = List.of(tune("Cooley's", "reel"), tune("The Kesh", "jig"));

        var statuses = IntStream.range(0, 6)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> create(list, UPLOADER_TOKEN).returnResult(SetDTO.class).getStatus().value()))
                .toList().stream().map(CompletableFuture::join).toList();

        assertThat(statuses).containsOnly(200, 201).filteredOn(s -> s == 201).hasSize(1);
        assertThat(count("set_sets", null)).isOne();
        assertThat(count("set_set_tunes", null)).isEqualTo(2);
    }

    // --- Requirement: Invalid Set requests ----------------------------------------------------------

    @Test
    void invalid_lists() {
        var cooleys = tune("Cooley's", "reel");

        create(List.of(), UPLOADER_TOKEN).expectStatus().isBadRequest();
        create(List.of(cooleys, "unknown"), UPLOADER_TOKEN).expectStatus().isBadRequest();
        create(java.util.Collections.nCopies(21, cooleys), UPLOADER_TOKEN).expectStatus().isBadRequest();

        assertThat(count("set_sets", null)).isZero();
    }

    @Test
    void anonymous_request() {
        create(List.of(tune("Cooley's", "reel")), null).expectStatus().isUnauthorized();
    }

    // --- Requirement: Browse Sets -------------------------------------------------------------------

    @Test
    void list_the_sets_of_a_tune() {
        var cooleys = tune("Cooley's", "reel");
        var kesh = tune("The Kesh", "jig");
        create(List.of(cooleys, kesh), UPLOADER_TOKEN).expectStatus().isCreated();
        create(List.of(kesh), UPLOADER_TOKEN).expectStatus().isCreated();

        var all = client.get().uri("/api/sets").exchange().expectStatus().isOk()
                .expectBodyList(SetDTO.class).returnResult().getResponseBody();
        var withCooleys = client.get().uri(uri -> uri.path("/api/sets").queryParam("tuneId", cooleys).build()).exchange()
                .expectStatus().isOk().expectBodyList(SetDTO.class).returnResult().getResponseBody();

        assertThat(all).hasSize(2);
        assertThat(withCooleys).singleElement().satisfies(set ->
                assertThat(set.tunes()).extracting(SetDTO.Entry::name).containsExactly("Cooley's", "The Kesh"));
    }

    @Test
    void get_a_set() {
        var set = create(List.of(tune("Cooley's", "reel")), UPLOADER_TOKEN)
                .expectBody(SetDTO.class).returnResult().getResponseBody();

        assertThat(client.get().uri("/api/sets/{id}", set.id()).exchange().expectStatus().isOk()
                .expectBody(SetDTO.class).returnResult().getResponseBody()).isEqualTo(set);
    }

    @Test
    void unknown_set() {
        client.get().uri("/api/sets/unknown").exchange().expectStatus().isNotFound();
    }

    // ---------------------------------------------------------------------------------------------

    /** Creates a tune by hand and returns its id. */
    String tune(String name, String danceType) {
        return client.post().uri("/api/tunes").headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("name", name, "danceType", danceType))
                .exchange().expectStatus().isCreated().expectBody(TuneDTO.class).returnResult().getResponseBody().id();
    }

    WebTestClient.ResponseSpec create(List<String> tuneIds, String token) {
        return client.post().uri("/api/sets").headers(h -> {
            if (token != null) h.setBearerAuth(token);
        }).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("tuneIds", tuneIds)).exchange();
    }
}
