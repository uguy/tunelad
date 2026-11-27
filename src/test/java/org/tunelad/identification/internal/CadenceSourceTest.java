package org.tunelad.identification.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the failure of the analysis service says, against a stub sidecar. */
class CadenceSourceTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private CadenceSource sidecarAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/identify", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        return new CadenceSource(new IdentificationProperties("http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(5), 0.5));
    }

    private static Flux<org.springframework.core.io.buffer.DataBuffer> audio() {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[]{1, 2, 3}));
    }

    @Test
    void a_refusal_reports_the_status_and_the_message() throws IOException {
        var source = sidecarAnswering(400, "{\"error\":\"The request body is the audio.\"}");

        assertThatThrownBy(() -> source.detectAny(audio()).collectList().block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("The request body is the audio.");
    }

    @Test
    void an_empty_body_reports_the_status_alone() throws IOException {
        var source = sidecarAnswering(503, "");

        assertThatThrownBy(() -> source.detectAny(audio()).collectList().block())
                .hasMessage("analysis service answered 503");
    }

    @Test
    void a_very_long_message_is_cut() throws IOException {
        var source = sidecarAnswering(500, "x".repeat(5000));

        assertThatThrownBy(() -> source.detectAny(audio()).collectList().block())
                .satisfies(error -> assertThat(error.getMessage()).hasSizeLessThan(400).endsWith("..."));
    }
}
