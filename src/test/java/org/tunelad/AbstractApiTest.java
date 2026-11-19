package org.tunelad;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.tunelad.track.TrackDTO;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Base for black-box REST API tests: the full application on a random port, backed by a
 * Testcontainers MongoDB, with the track library cleared before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainerDefinitions.class)
public abstract class AbstractApiTest {

    protected static final String KNEES_UP_MP3 = "mp3/Knees_Up.mp3";
    protected static final String MOSSY_BANKS_MP3 = "mp3/Stephen_Doherty_Patrick_Doocey-Mossy_banks_Ravens_rock.mp3";

    @LocalServerPort
    int port;

    protected WebTestClient client;

    @BeforeEach
    void clearLibrary() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                // play responses carry whole mp3 fixtures (~3 MB), above the 256 KB default
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        client.delete().uri("/api/tracks").exchange().expectStatus().is2xxSuccessful();
    }

    /** Uploads a track; a null value omits that part, and a null fixture omits the file part. */
    protected WebTestClient.ResponseSpec upload(Map<String, String> parts, String fixture) {
        var builder = new MultipartBodyBuilder();
        parts.forEach((name, value) -> {
            if (value != null) builder.part(name, value);
        });
        if (fixture != null) builder.part("file", new ClassPathResource(fixture));
        return client.post().uri("/api/tracks")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    protected void uploadOk(String title, String description, String fixture) {
        upload(Map.of("title", title, "description", description), fixture)
                .expectStatus().is2xxSuccessful();
    }

    /** Requests playback with "Range: bytes=0-", as a browser audio element does (see design.md). */
    protected WebTestClient.ResponseSpec play(String id) {
        return play(id, "bytes=0-");
    }

    /** Requests playback with the given Range header value; null sends no Range header. */
    protected WebTestClient.ResponseSpec play(String id, String range) {
        var request = client.get().uri("/api/tracks/{id}/play", id);
        if (range != null) request.header(HttpHeaders.RANGE, range);
        return request.exchange();
    }

    protected List<TrackDTO> listTracks() {
        return client.get().uri("/api/tracks").exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
    }

    protected TrackDTO trackTitled(String title) {
        return listTracks().stream()
                .filter(t -> title.equals(t.title()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No track titled " + title));
    }
}
