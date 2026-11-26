package org.tunelad.live.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.tunelad.media.event.WaveformComputed;
import org.tunelad.search.event.TrackIndexed;
import org.tunelad.search.event.TrackUnindexed;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The events of the modules that own the state, on their way out to NATS. */
class LiveBridgeTest extends AbstractLiveTest {

    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper json;

    @Test
    void a_track_that_became_searchable_goes_out_to_everyone() throws Exception {
        var message = publishedAfter(new TrackIndexed("42"));

        assertThat(message.subject()).isEqualTo("tunelad.library.track.indexed");
        assertThat(message.body()).isEqualTo("{\"type\":\"track.indexed\",\"id\":\"42\"}");
    }

    @Test
    void a_track_that_stopped_being_searchable_goes_out_to_everyone() throws Exception {
        var message = publishedAfter(new TrackUnindexed("42"));

        assertThat(message.subject()).isEqualTo("tunelad.library.track.unindexed");
        assertThat(message.body()).isEqualTo("{\"type\":\"track.unindexed\",\"id\":\"42\"}");
    }

    @Test
    void a_computed_waveform_goes_out_under_the_id_of_its_track() throws Exception {
        // The audio's key is the id of its track
        var message = publishedAfter(new WaveformComputed("42"));

        assertThat(message.subject()).isEqualTo("tunelad.library.waveform.computed");
        assertThat(message.body()).isEqualTo("{\"type\":\"waveform.computed\",\"id\":\"42\"}");
    }

    @Test
    void an_id_with_quotes_and_backslashes_stays_valid_json() throws Exception {
        var id = "a\"b\\c d";

        var message = publishedAfter(new TrackIndexed(id));

        assertThat(json.readTree(message.body()).get("id").asString()).isEqualTo(id);
        assertThat(message.subject()).isEqualTo("tunelad.library.track.indexed");
    }

    @Test
    void the_event_is_completed_in_the_registry_so_it_is_never_replayed() throws Exception {
        publishedAfter(new TrackIndexed("42"));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> count("event_publication", "completion_date IS NULL") == 0);
    }

    private record Published(String subject, String body) {
    }

    /** Publishes the event in a transaction, as its module does, and returns what reached NATS. */
    private Published publishedAfter(Object event) throws Exception {
        var subscription = nats().subscribe("tunelad.library.>");
        nats().flush(Duration.ofSeconds(5));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> events.publishEvent(event));

        var message = subscription.nextMessage(Duration.ofSeconds(10));
        assertThat(message).as("a message on NATS").isNotNull();
        return new Published(message.getSubject(), new String(message.getData(), StandardCharsets.UTF_8));
    }
}
