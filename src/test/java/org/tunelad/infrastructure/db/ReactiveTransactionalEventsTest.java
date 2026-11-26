package org.tunelad.infrastructure.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Spike turned regression test (openspec change search-read-model, design D1): how Spring Modulith's
 * {@code @ApplicationModuleListener} and Event Publication Registry behave with an event published from a reactive
 * R2DBC transaction.
 */
@SpringBootTest
@Import({TestContainerDefinitions.class, ReactiveTransactionalEventsTest.Spike.class})
class ReactiveTransactionalEventsTest {

    record SpikeEvent(String id) {}

    @Autowired SpikeService service;
    @Autowired DatabaseClient db;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        db.sql("CREATE TABLE IF NOT EXISTS spike (id TEXT PRIMARY KEY)").then().block();
        SpikeListener.handled.clear();
        SpikeListener.published.clear();
    }

    // D1 question 1 and 4: delivered after commit, asynchronously
    @Test
    void event_reaches_module_listener_after_commit_on_another_thread() {
        var id = UUID.randomUUID().toString();

        StepVerifier.create(service.saveAndPublish(id, false)).verifyComplete();

        await().atMost(Duration.ofSeconds(5)).until(() -> SpikeListener.handled.containsKey(id));
        assertThat(SpikeListener.handled.get(id)).doesNotStartWith("reactor-http").doesNotContain("nioEventLoop");
        assertThat(inserted(id)).isTrue();
    }

    // D1 question 1: not delivered on rollback
    @Test
    void event_never_reaches_module_listener_on_rollback() {
        var id = UUID.randomUUID().toString();

        StepVerifier.create(service.saveAndPublish(id, true)).verifyError(IllegalStateException.class);

        // The event was published inside the transaction...
        assertThat(SpikeListener.published).containsKey(id);
        // ...but the transaction rolled back, so neither the document nor the delivery happened.
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2))
                .until(() -> !SpikeListener.handled.containsKey(id));
        assertThat(inserted(id)).isFalse();
    }

    // D1 question 2: the registry stores the publication with blocking calls, on the publishing thread
    @Test
    void publication_is_stored_off_the_event_loop() {
        var id = UUID.randomUUID().toString();

        StepVerifier.create(service.saveAndPublish(id, false)).verifyComplete();

        // A plain listener runs on the publishing thread, right after the registry stored the publication.
        // Without TransactionalEvents' scheduler, this was an event loop thread of the reactive driver.
        assertThat(SpikeListener.published.get(id)).startsWith("boundedElastic");
    }

    // D1 question 3: the registry writes in its own blocking transaction, not in the reactive one
    @Test
    void rolled_back_transaction_leaves_an_incomplete_publication() {
        var id = UUID.randomUUID().toString();

        StepVerifier.create(service.saveAndPublish(id, true)).verifyError(IllegalStateException.class);

        var publication = jdbc.queryForMap(
                "SELECT status, completion_date FROM event_publication WHERE serialized_event LIKE ?", "%" + id + "%");
        assertThat(publication.get("status")).isEqualTo("PUBLISHED");
        assertThat(publication.get("completion_date")).isNull();
    }

    private boolean inserted(String id) {
        return db.sql("SELECT count(*) AS n FROM spike WHERE id = :id").bind("id", id)
                .map(row -> row.get("n", Long.class)).one().block() > 0;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Spike {

        @Bean
        SpikeService spikeService(DatabaseClient db, TransactionalEvents events) {
            return new SpikeService(db, events);
        }

        @Bean
        SpikeListener spikeListener() {
            return new SpikeListener();
        }
    }

    static class SpikeService {

        private final DatabaseClient db;
        private final TransactionalEvents events;

        SpikeService(DatabaseClient db, TransactionalEvents events) {
            this.db = db;
            this.events = events;
        }

        @ReactiveTransactional
        public Mono<Void> saveAndPublish(String id, boolean failAfterPublishing) {
            return db.sql("INSERT INTO spike (id) VALUES (:id)").bind("id", id).then()
                    .then(events.publish(new SpikeEvent(id)))
                    .then(failAfterPublishing ? Mono.error(new IllegalStateException("rollback")) : Mono.empty());
        }
    }

    static class SpikeListener {

        // Static: the bean is a proxy (@Async, @Transactional), whose fields are not the target's.
        /** Event id to the thread that published it (a plain listener runs on the publishing thread). */
        static final Map<String, String> published = new ConcurrentHashMap<>();
        /** Event id to the thread the module listener ran on. */
        static final Map<String, String> handled = new ConcurrentHashMap<>();

        @EventListener
        void onPublish(SpikeEvent event) {
            published.put(event.id(), Thread.currentThread().getName());
        }

        @ApplicationModuleListener
        void on(SpikeEvent event) {
            handled.put(event.id(), Thread.currentThread().getName());
        }
    }
}
