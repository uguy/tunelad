package org.tunelad.infrastructure;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalEventPublisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Publishes application events from a reactive transaction, so that transactional listeners (including
 * {@code @ApplicationModuleListener}) run after it commits, and never when it rolls back.
 * <p>
 * The Spring Modulith Event Publication Registry stores each publication with blocking MongoDB calls while the
 * event is published. Publishing therefore runs on the bounded elastic scheduler, never on an event loop thread.
 */
@Component
public class TransactionalEvents {

    private final TransactionalEventPublisher publisher;

    TransactionalEvents(ApplicationEventPublisher events) {
        this.publisher = new TransactionalEventPublisher(events);
    }

    /** Publishes the event once subscribed, within the subscriber's reactive transaction. */
    public Mono<Void> publish(Object event) {
        return Mono.defer(() -> publisher.publishEvent(event)).subscribeOn(Schedulers.boundedElastic());
    }
}
