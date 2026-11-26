package org.tunelad.live.internal;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Live events over core NATS: no persistence and no replay, which is what hints that clients refetch on need.
 * <p>
 * The application starts without NATS and picks it up when it becomes reachable: the client connects in the
 * background and keeps reconnecting without limit, and subscriptions are restored on reconnection. Nothing is
 * buffered while disconnected, because a stale hint is worth nothing. Publishing is non-blocking in the client, so it
 * is safe from any thread.
 * <p>
 * The client hands over its connection through a listener, the first time it reports anything. Until then, and while
 * it is disconnected, events are dropped.
 */
@Slf4j
@Component
class NatsLiveBus implements LiveBus {

    private record Pending(String subject, BiConsumer<String, String> handler) {
    }

    private final Object lock = new Object();
    private final List<Pending> pending = new ArrayList<>();
    private final AtomicBoolean failing = new AtomicBoolean();
    private volatile @Nullable Connection connection;
    private ConnectionListener.@Nullable Events lastEvent;

    NatsLiveBus(LiveProperties properties) {
        var options = new Options.Builder()
                .server(properties.nats().url())
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(1))
                .connectionTimeout(Duration.ofSeconds(2))
                .reconnectBufferSize(0)
                .connectionListener(this::onConnectionEvent)
                .errorListener(new QuietErrors())
                .build();
        try {
            Nats.connectAsynchronously(options, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the NATS client", e);
        }
    }

    @Override
    public void publish(String subject, String body) {
        var connected = connection;
        if (connected == null) {
            dropped(subject, "NATS has not been reached yet");
            return;
        }
        try {
            connected.publish(subject, body.getBytes(StandardCharsets.UTF_8));
            if (failing.compareAndSet(true, false)) {
                log.info("Live events are flowing again");
            }
        } catch (RuntimeException e) {
            dropped(subject, e.toString());
        }
    }

    @Override
    public void subscribe(String subject, BiConsumer<String, String> handler) {
        synchronized (lock) {
            var connected = connection;
            if (connected == null) {
                pending.add(new Pending(subject, handler));
            } else {
                attach(connected, subject, handler);
            }
        }
    }

    @PreDestroy
    void close() throws InterruptedException {
        var connected = connection;
        if (connected != null) {
            connected.close();
        }
    }

    private void onConnectionEvent(Connection connected, ConnectionListener.Events event) {
        synchronized (lock) {
            if (connection == null) {
                connection = connected;
                pending.forEach(p -> attach(connected, p.subject(), p.handler()));
                pending.clear();
            }
            // Every failed attempt reports a disconnection: log the changes only
            if (event != lastEvent) {
                lastEvent = event;
                log.info("NATS {}", event.getEvent());
            }
        }
    }

    private static void attach(Connection connected, String subject, BiConsumer<String, String> handler) {
        connected.createDispatcher(message ->
                handler.accept(message.getSubject(), new String(message.getData(), StandardCharsets.UTF_8)))
                .subscribe(subject);
    }

    /** Live events are best effort: warn once per outage, not for every event. */
    private void dropped(String subject, String why) {
        if (failing.compareAndSet(false, true)) {
            log.warn("Live events are not reaching NATS, they are dropped until it is back: {}", why);
        } else {
            log.debug("Dropped a live event on {}: {}", subject, why);
        }
    }

    /** What the client reports while NATS is unreachable is expected: the default listener logs an error each second. */
    private static final class QuietErrors implements ErrorListener {
        @Override
        public void errorOccurred(Connection connection, String error) {
            log.debug("NATS error: {}", error);
        }

        @Override
        public void exceptionOccurred(Connection connection, Exception exception) {
            log.debug("NATS exception: {}", exception.toString());
        }
    }
}
