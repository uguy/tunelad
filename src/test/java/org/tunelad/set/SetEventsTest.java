package org.tunelad.set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.set.event.SetChanged;
import org.tunelad.tune.TuneDTO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Events the set module publishes, recorded by a plain listener (see TrackEventsTest for why). */
@Import(SetEventsTest.Recorder.class)
class SetEventsTest extends AbstractApiTest {

    static final List<SetChanged> changed = new CopyOnWriteArrayList<>();

    @BeforeEach
    void forgetEvents() {
        changed.clear();
    }

    @Test
    void creating_a_set_publishes_set_changed_once_with_its_id() {
        var tunes = List.of(tune("Cooley's", "reel"), tune("The Kesh", "jig"));

        var set = post(tunes).expectStatus().isCreated().expectBody(SetDTO.class).returnResult().getResponseBody();

        assertThat(changed).containsExactly(new SetChanged(set.id()));
    }

    @Test
    void finding_an_existing_set_or_a_refused_list_publishes_nothing() {
        var tunes = List.of(tune("Cooley's", "reel"));
        post(tunes).expectStatus().isCreated();
        changed.clear();

        post(tunes).expectStatus().isOk();
        post(List.of()).expectStatus().isBadRequest();

        assertThat(changed).isEmpty();
    }

    String tune(String name, String danceType) {
        return client.post().uri("/api/tunes").headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("name", name, "danceType", danceType))
                .exchange().expectStatus().isCreated().expectBody(TuneDTO.class).returnResult().getResponseBody().id();
    }

    WebTestClient.ResponseSpec post(List<String> tuneIds) {
        return client.post().uri("/api/sets").headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("tuneIds", tuneIds)).exchange();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {
        @Bean
        RecordingListener setRecordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {
        @EventListener
        void on(SetChanged event) {
            changed.add(event);
        }
    }
}
