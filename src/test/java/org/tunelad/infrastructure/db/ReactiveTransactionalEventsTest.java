package org.tunelad.infrastructure.db;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
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
 * MongoDB transaction.
 */
@SpringBootTest
@Import({TestContainerDefinitions.class, ReactiveTransactionalEventsTest.Spike.class})
class ReactiveTransactionalEventsTest {

    record SpikeEvent(String id) {}

    @Autowired SpikeService service;
    @Autowired ReactiveMongoTemplate reactiveMongo;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void reset() {
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
        assertThat(reactiveMongo.exists(byId(id), "spike").block()).isTrue();
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
        assertThat(reactiveMongo.exists(byId(id), "spike").block()).isFalse();
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

        var publication = mongo.findOne(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("event._id").is(id)),
                Document.class, "event_publication");
        assertThat(publication).isNotNull();
        assertThat(publication.getString("status")).isEqualTo("PUBLISHED");
        assertThat(publication.get("completionDate")).isNull();
    }

    private static org.springframework.data.mongodb.core.query.Query byId(String id) {
        return org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(id));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Spike {

        @Bean
        SpikeService spikeService(ReactiveMongoTemplate mongo, TransactionalEvents events) {
            return new SpikeService(mongo, events);
        }

        @Bean
        SpikeListener spikeListener() {
            return new SpikeListener();
        }
    }

    static class SpikeService {

        private final ReactiveMongoTemplate mongo;
        private final TransactionalEvents events;

        SpikeService(ReactiveMongoTemplate mongo, TransactionalEvents events) {
            this.mongo = mongo;
            this.events = events;
        }

        @ReactiveTransactional
        public Mono<Void> saveAndPublish(String id, boolean failAfterPublishing) {
            return mongo.insert(new Document("_id", id), "spike")
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
