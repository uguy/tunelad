package org.tunelad.tune.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tunelad.tune.DanceType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The thesession.org client against a local server answering like thesession.org does. */
class ThesessionTuneSourceTest {

    HttpServer server;
    ThesessionTuneSource source;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/tunes/27", exchange -> reply(exchange, 200, """
                {"id":27,"name":"Cooley's","type":"reel","aliases":["Cooley's Reel"],
                 "settings":[{"key":"Edorian","abc":"EBBA B2 EB|\\r\\nB2 AB dBAG|"}]}"""));
        server.createContext("/tunes/500", exchange -> reply(exchange, 503, ""));
        server.start();
        source = new ThesessionTuneSource("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void reads_a_tune_as_complete_abc() {
        var tune = source.fetch(27).block();

        assertThat(tune.name()).isEqualTo("Cooley's");
        assertThat(tune.danceType()).isEqualTo(DanceType.REEL);
        assertThat(tune.aliases()).containsExactly("Cooley's Reel");
        assertThat(tune.key()).isEqualTo("Edorian");
        assertThat(tune.abc()).isEqualTo("X:1\nT:Cooley's\nM:4/4\nL:1/8\nK:Edor\nEBBA B2 EB|\nB2 AB dBAG|");
    }

    @Test
    void unknown_id_is_empty() {
        assertThat(source.fetch(1).blockOptional()).isEmpty();
    }

    @Test
    void server_error_is_unavailable() {
        assertThatThrownBy(() -> source.fetch(500).block()).isInstanceOf(TuneSource.SourceUnavailableException.class);
    }

    @Test
    void keys_become_abc_keys() {
        assertThat(ThesessionTuneSource.abcKey("Gmajor")).isEqualTo("G");
        assertThat(ThesessionTuneSource.abcKey("Aminor")).isEqualTo("Am");
        assertThat(ThesessionTuneSource.abcKey("Amixolydian")).isEqualTo("Amix");
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
