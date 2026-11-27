package org.tunelad.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.comment.event.CommentChanged;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The events the comment module publishes: one per change, none when refused. */
@Import(CommentEventsTest.Recorder.class)
class CommentEventsTest extends AbstractApiTest {

    static final List<CommentChanged> changed = new CopyOnWriteArrayList<>();

    @BeforeEach
    void forgetEvents() {
        changed.clear();
    }

    @Test
    void posting_editing_and_deleting_each_publish_once() {
        var tune = createTune("The Kesh", "jig");

        var comment = post(tune, "Hello").expectStatus().isCreated()
                .expectBody(CommentDTO.class).returnResult().getResponseBody();
        client.patch().uri("/api/comments/{id}", comment.id()).headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .bodyValue(Map.of("text", "Hi")).exchange().expectStatus().isOk();
        client.delete().uri("/api/comments/{id}", comment.id()).headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .exchange().expectStatus().isNoContent();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(changed).containsExactly(new CommentChanged(tune), new CommentChanged(tune), new CommentChanged(tune)));
    }

    @Test
    void a_refused_comment_publishes_nothing() {
        var tune = createTune("The Kesh", "jig");

        post(tune, " ").expectStatus().isBadRequest();

        assertThat(changed).isEmpty();
    }

    @Test
    void deleting_a_track_publishes_for_it() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        client.post().uri("/api/tracks/{id}/comments", kneesUp.id()).headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .bodyValue(Map.of("text", "Hello")).exchange().expectStatus().isCreated();
        changed.clear();

        delete(kneesUp.id(), UPLOADER_TOKEN).expectStatus().isNoContent();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(changed).containsExactly(new CommentChanged(kneesUp.id())));
    }

    private WebTestClient.ResponseSpec post(String tuneId, String text) {
        return client.post().uri("/api/tunes/{id}/comments", tuneId).headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .bodyValue(Map.of("text", text)).exchange();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {

        @Bean
        RecordingListener commentRecordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {

        @EventListener
        void on(CommentChanged event) {
            changed.add(event);
        }
    }
}
