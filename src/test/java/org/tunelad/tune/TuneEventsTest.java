package org.tunelad.tune;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.tune.event.TuneChanged;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Events the tune module publishes, recorded by a plain listener (see TrackEventsTest for why). */
@Import({TuneApiTest.StubSource.class, TuneEventsTest.Recorder.class})
class TuneEventsTest extends AbstractApiTest {

    static final List<TuneChanged> changed = new CopyOnWriteArrayList<>();

    @BeforeEach
    void forgetEvents() {
        changed.clear();
    }

    @Test
    void creating_a_tune_publishes_tune_changed_once_with_its_id() {
        var tune = post("/api/tunes", Map.of("name", "The Kesh", "danceType", "jig")).expectStatus().isCreated()
                .expectBody(TuneDTO.class).returnResult().getResponseBody();

        assertThat(changed).containsExactly(new TuneChanged(tune.id()));
    }

    @Test
    void importing_a_tune_publishes_tune_changed_only_when_it_is_created() {
        var tune = post("/api/tunes/import", Map.of("id", 27)).expectStatus().isCreated()
                .expectBody(TuneDTO.class).returnResult().getResponseBody();
        post("/api/tunes/import", Map.of("id", 27)).expectStatus().isOk();

        assertThat(changed).containsExactly(new TuneChanged(tune.id()));
    }

    @Test
    void refused_creations_publish_nothing() {
        post("/api/tunes", Map.of("name", " ", "danceType", "jig")).expectStatus().isBadRequest();
        post("/api/tunes/import", Map.of("id", 0)).expectStatus().isNotFound();

        assertThat(changed).isEmpty();
    }

    private WebTestClient.ResponseSpec post(String uri, Map<String, ?> body) {
        return client.post().uri(uri).headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {
        @Bean
        RecordingListener tuneRecordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {
        @EventListener
        void on(TuneChanged event) {
            changed.add(event);
        }
    }
}
