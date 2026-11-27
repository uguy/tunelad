package org.tunelad.live.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the live updates do, as metrics: how many connections are open, and how many events were published and
 * delivered by type. The type is the only tag, and only the types the application raises are tag values (anything
 * else, which a stranger could publish on NATS, counts as {@code other}), so the number of series stays bounded and
 * no id ever becomes one.
 */
@Component
public class LiveMetrics {

    /** The event types the application raises. */
    static final Set<String> TYPES = Set.of("track.indexed", "track.unindexed", "waveform.computed",
            "favourite.changed", "playlist.changed");

    private final MeterRegistry registry;
    private final AtomicInteger connections = new AtomicInteger();

    LiveMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("tunelad.live.connections", connections, AtomicInteger::get)
                .description("Open live update connections (Server-Sent Events) on this instance")
                .register(registry);
    }

    public void connectionOpened() {
        connections.incrementAndGet();
    }

    public void connectionClosed() {
        connections.decrementAndGet();
    }

    /** An event was handed to NATS. */
    void published(String type) {
        counter("tunelad.live.events.published", "Live events this instance published, by type", type).increment();
    }

    /** An event was sent to one connection. */
    public void delivered(String type) {
        counter("tunelad.live.events.delivered", "Live events sent to connections on this instance, by type", type)
                .increment();
    }

    private Counter counter(String name, String description, String type) {
        return Counter.builder(name).description(description).tag("type", TYPES.contains(type) ? type : "other")
                .register(registry);
    }
}
