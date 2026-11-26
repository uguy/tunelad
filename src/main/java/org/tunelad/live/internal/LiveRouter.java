package org.tunelad.live.internal;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Receives every live event published by any instance, and hands each to the connections it is meant for.
 * <p>
 * One subscription serves the whole instance. Each connection then filters the events by its own identity, which the
 * server decides: nothing a client sends takes part in it. A connection that does not keep up misses events, without
 * holding up the others.
 */
@Slf4j
@Component
public class LiveRouter {

    private final Sinks.Many<Routed> events = Sinks.many().multicast().directBestEffort();

    LiveRouter(LiveBus bus) {
        bus.subscribe(LiveSubjects.ALL, this::route);
    }

    /**
     * The events for a viewer: the library's, and those of the viewer's own account.
     *
     * @param viewerKey the viewer's account key, null for an anonymous viewer
     */
    public Flux<Routed> stream(@Nullable String viewerKey) {
        return events.asFlux().filter(routed -> routed.audience().admits(viewerKey));
    }

    /** Routes one message. Called one message at a time: the bus delivers its messages in turn. */
    void route(String subject, @Nullable String body) {
        LiveSubjects.route(subject, body).ifPresentOrElse(
                routed -> events.tryEmitNext(routed),
                () -> log.debug("Ignored a message on {}", subject));
    }
}
