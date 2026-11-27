package org.tunelad.comment;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Tests for openspec/specs/comment: one test per spec scenario, named after it. */
class CommentApiTest extends AbstractApiTest {

    static final String BOB_TOKEN = TestTokens.bearer("bob", "Bob Smith");
    static final String CAROL_TOKEN = TestTokens.bearer("carol", "Carol Jones");

    // --- Requirement: Read comments on a subject --------------------------------------------

    @Test
    void anonymous_reader() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        post("tracks", kneesUp.id(), "Lovely flute", UPLOADER_TOKEN).expectStatus().isCreated();

        var comments = list("tracks", kneesUp.id());

        assertThat(comments).singleElement().satisfies(c -> {
            assertThat(c.text()).isEqualTo("Lovely flute");
            assertThat(c.authorName()).isEqualTo("Alice Liddell");
            assertThat(c.editedAt()).isNull();
        });
    }

    @Test
    void oldest_first() {
        var tune = createTune("The Kesh", "jig");
        post("tunes", tune, "First", UPLOADER_TOKEN).expectStatus().isCreated();
        post("tunes", tune, "Second", BOB_TOKEN).expectStatus().isCreated();

        assertThat(list("tunes", tune)).extracting(CommentDTO::text).containsExactly("First", "Second");
    }

    @Test
    void unknown_subject() {
        for (var kind : List.of("tracks", "tunes", "sets")) {
            client.get().uri("/api/{kind}/unknown/comments", kind).exchange().expectStatus().isNotFound();
        }
    }

    // --- Requirement: Post a comment ---------------------------------------------------------

    @Test
    void post_on_a_set() {
        var set = createSet(createTune("The Kesh", "jig"));

        var created = post("sets", set.id(), "  Great pairing ", UPLOADER_TOKEN)
                .expectStatus().isCreated().expectBody(CommentDTO.class).returnResult().getResponseBody();

        assertThat(created.text()).isEqualTo("Great pairing");
        assertThat(created.authorId()).isEqualTo(accountOf(UPLOADER_TOKEN).id());
        assertThat(list("sets", set.id())).containsExactly(created);
    }

    @Test
    void blank_text() {
        var tune = createTune("The Kesh", "jig");

        post("tunes", tune, "   ", UPLOADER_TOKEN).expectStatus().isBadRequest();

        assertThat(count("comment_comments", null)).isZero();
    }

    @Test
    void text_too_long() {
        var tune = createTune("The Kesh", "jig");

        post("tunes", tune, "x".repeat(2001), UPLOADER_TOKEN).expectStatus().isBadRequest();
        post("tunes", tune, "x".repeat(2000), UPLOADER_TOKEN).expectStatus().isCreated();
    }

    @Test
    void markup_is_stored_verbatim() {
        var tune = createTune("The Kesh", "jig");

        post("tunes", tune, "<b>hi</b>", UPLOADER_TOKEN).expectStatus().isCreated();

        assertThat(list("tunes", tune)).extracting(CommentDTO::text).containsExactly("<b>hi</b>");
    }

    @Test
    void anonymous_post() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        post("tracks", kneesUp.id(), "Hello", null).expectStatus().isUnauthorized();
        post("tracks", "unknown", "Hello", UPLOADER_TOKEN).expectStatus().isNotFound();

        assertThat(count("comment_comments", null)).isZero();
    }

    // --- Requirement: Edit and delete a comment ----------------------------------------------

    @Test
    void author_edits() {
        var tune = createTune("The Kesh", "jig");
        var comment = postOk("tunes", tune, "Old", UPLOADER_TOKEN);

        var edited = send(client.patch(), comment.id(), "New", UPLOADER_TOKEN)
                .expectStatus().isOk().expectBody(CommentDTO.class).returnResult().getResponseBody();

        assertThat(edited.text()).isEqualTo("New");
        assertThat(edited.editedAt()).isNotNull();
    }

    @Test
    void administrator_deletes() {
        var tune = createTune("The Kesh", "jig");
        var comment = postOk("tunes", tune, "Rude", UPLOADER_TOKEN);

        deleteComment(comment.id(), TestTokens.admin()).expectStatus().isNoContent();

        assertThat(list("tunes", tune)).isEmpty();
    }

    @Test
    void another_account_is_refused() {
        var tune = createTune("The Kesh", "jig");
        var comment = postOk("tunes", tune, "Mine", UPLOADER_TOKEN);

        deleteComment(comment.id(), CAROL_TOKEN).expectStatus().isForbidden();
        send(client.patch(), comment.id(), "Hijack", CAROL_TOKEN).expectStatus().isForbidden();
        deleteComment(comment.id(), null).expectStatus().isUnauthorized();
        deleteComment("unknown", UPLOADER_TOKEN).expectStatus().isNotFound();

        assertThat(list("tunes", tune)).containsExactly(comment);
    }

    // --- Requirement: Comments leave with their subject --------------------------------------

    @Test
    void track_deleted() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        post("tracks", kneesUp.id(), "One", UPLOADER_TOKEN).expectStatus().isCreated();
        post("tracks", kneesUp.id(), "Two", BOB_TOKEN).expectStatus().isCreated();

        delete(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(count("comment_comments", null)).isZero());
    }

    // ---------------------------------------------------------------------------------------------

    WebTestClient.ResponseSpec post(String kind, String id, String text, String token) {
        var request = client.post().uri("/api/{kind}/{id}/comments", kind, id).bodyValue(Map.of("text", text));
        return token == null ? request.exchange() : request.headers(h -> h.setBearerAuth(token)).exchange();
    }

    CommentDTO postOk(String kind, String id, String text, String token) {
        return post(kind, id, text, token).expectStatus().isCreated()
                .expectBody(CommentDTO.class).returnResult().getResponseBody();
    }

    WebTestClient.ResponseSpec send(WebTestClient.RequestBodyUriSpec method, String id, String text, String token) {
        return method.uri("/api/comments/{id}", id).headers(h -> h.setBearerAuth(token))
                .bodyValue(Map.of("text", text)).exchange();
    }

    WebTestClient.ResponseSpec deleteComment(String id, String token) {
        var request = client.delete().uri("/api/comments/{id}", id);
        return token == null ? request.exchange() : request.headers(h -> h.setBearerAuth(token)).exchange();
    }

    List<CommentDTO> list(String kind, String id) {
        return client.get().uri("/api/{kind}/{id}/comments", kind, id).exchange()
                .expectStatus().isOk().expectBodyList(CommentDTO.class).returnResult().getResponseBody();
    }
}
