package org.tunelad.live.internal;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscription;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** A client of {@code GET /api/live} that keeps what it receives. */
final class LiveStream implements AutoCloseable {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> EVENT = new ParameterizedTypeReference<>() {};

    private final List<ServerSentEvent<String>> received = new CopyOnWriteArrayList<>();
    private final Disposable subscription;
    final HttpStatusCode status;
    final @Nullable MediaType contentType;

    private LiveStream(WebTestClient client, String uri, @Nullable String token, boolean reading,
                       Consumer<HttpHeaders> more) {
        var result = client.get().uri(uri).accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> {
                    if (token != null) headers.setBearerAuth(token);
                    more.accept(headers);
                })
                .exchange()
                .returnResult(EVENT);
        this.status = result.getStatus();
        this.contentType = result.getResponseHeaders().getContentType();
        if (reading) {
            this.subscription = result.getResponseBody().subscribe(received::add, error -> { });
        } else {
            // Connected, but never asks for an event: a client that has stopped reading
            var stalled = new BaseSubscriber<ServerSentEvent<String>>() {
                @Override
                protected void hookOnSubscribe(Subscription subscription) {
                }
            };
            result.getResponseBody().subscribe(stalled);
            this.subscription = stalled;
        }
    }

    /** Connects, and reads as events come. */
    static LiveStream open(WebTestClient client, @Nullable String token) {
        return open(client, "/api/live", token);
    }

    static LiveStream open(WebTestClient client, String uri, @Nullable String token) {
        return new LiveStream(client, uri, token, true, headers -> { });
    }

    /** Connects with extra request headers, as a client trying to choose its audience might send. */
    static LiveStream open(WebTestClient client, @Nullable String token, Consumer<HttpHeaders> more) {
        return new LiveStream(client, "/api/live", token, true, more);
    }

    /** Connects and never reads. */
    static LiveStream openStalled(WebTestClient client) {
        return new LiveStream(client, "/api/live", null, false, headers -> { });
    }

    /** The events received so far, in order, without the comment lines. */
    List<ServerSentEvent<String>> events() {
        return received.stream().filter(event -> event.event() != null).toList();
    }

    /** The event names received so far. */
    List<String> names() {
        return events().stream().map(ServerSentEvent::event).toList();
    }

    /** The data of the received events with that name. */
    List<String> dataOf(String name) {
        return events().stream().filter(event -> name.equals(event.event())).map(ServerSentEvent::data).toList();
    }

    /** How many comment lines (heartbeats) were received. */
    long comments() {
        return received.stream().filter(event -> event.comment() != null).count();
    }

    @Override
    public void close() {
        subscription.dispose();
    }
}
