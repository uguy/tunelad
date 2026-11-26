package org.tunelad.live.internal;

import io.nats.client.Connection;
import io.nats.client.Nats;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Value;
import org.tunelad.AbstractApiTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Base for tests of live updates: the application on a random port, a NATS of its own, and a way to follow the stream. */
abstract class AbstractLiveTest extends AbstractApiTest {

    @Value("${tunelad.live.nats.url}")
    String natsUrl;

    private Connection nats;
    private final List<LiveStream> streams = new ArrayList<>();

    /** A NATS client of the test, to publish and listen the way another instance would. */
    protected Connection nats() throws IOException, InterruptedException {
        if (nats == null) {
            nats = Nats.connect(natsUrl);
        }
        return nats;
    }

    /** Publishes on NATS as another instance would. */
    protected void publishRaw(String subject, String body) throws Exception {
        nats().publish(subject, body.getBytes(StandardCharsets.UTF_8));
        nats().flush(java.time.Duration.ofSeconds(5));
    }

    /** Opens the live stream, anonymously when the token is null; closed after the test. */
    protected LiveStream follow(String token) {
        var stream = LiveStream.open(client, token);
        streams.add(stream);
        return stream;
    }

    protected LiveStream follow(String uri, String token) {
        var stream = LiveStream.open(client, uri, token);
        streams.add(stream);
        return stream;
    }

    @AfterEach
    void closeLive() throws InterruptedException {
        streams.forEach(LiveStream::close);
        streams.clear();
        if (nats != null) {
            nats.close();
            nats = null;
        }
    }
}
