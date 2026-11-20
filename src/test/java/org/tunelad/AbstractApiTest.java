package org.tunelad;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.tunelad.account.AccountRef;
import org.tunelad.track.TrackDTO;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Base for black-box REST API tests: the full application on a random port, backed by a
 * Testcontainers MongoDB, emptied before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainerDefinitions.class)
public abstract class AbstractApiTest {

    protected static final String KNEES_UP_MP3 = "mp3/Knees_Up.mp3";
    protected static final String MOSSY_BANKS_MP3 = "mp3/Stephen_Doherty_Patrick_Doocey-Mossy_banks_Ravens_rock.mp3";

    /** Uploads authenticate as alice with a bearer token, which also spares them the CSRF token. */
    protected static final String UPLOADER_TOKEN = TestTokens.bearer("alice", "Alice Liddell");

    @LocalServerPort
    protected int port;

    protected WebTestClient client;

    @Autowired
    protected ReactiveMongoTemplate mongo;

    @BeforeEach
    void emptyDatabase() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                // play responses carry whole mp3 fixtures (~3 MB), above the 256 KB default
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        // Deletes the documents of every collection, whichever module owns it, but keeps the collections
        // and their indexes: those are only created at startup, and search relies on its text index.
        mongo.getCollectionNames()
                .filter(name -> !name.startsWith("system."))
                .concatMap(name -> mongo.getCollection(name).flatMap(c -> Mono.from(c.deleteMany(new Document()))))
                .then()
                .block(Duration.ofSeconds(30));
    }

    /** Uploads a track as alice; a null value omits that part, and a null fixture omits the file part. */
    protected WebTestClient.ResponseSpec upload(Map<String, String> parts, String fixture) {
        return upload(parts, fixture, UPLOADER_TOKEN);
    }

    /** Uploads a track with the given bearer token, or anonymously when it is null. */
    protected WebTestClient.ResponseSpec upload(Map<String, String> parts, String fixture, String token) {
        return uploadFile(parts, fixture == null ? null : new ClassPathResource(fixture), token);
    }

    /**
     * Uploads a track with the given file (streamed, so a large temporary file is not held in memory), as alice;
     * a null file omits the file part. The part's file name is the resource's.
     */
    protected WebTestClient.ResponseSpec uploadFile(Map<String, String> parts, Resource file) {
        return uploadFile(parts, file, UPLOADER_TOKEN);
    }

    private WebTestClient.ResponseSpec uploadFile(Map<String, String> parts, Resource file, String token) {
        var builder = new MultipartBodyBuilder();
        parts.forEach((name, value) -> {
            if (value != null) builder.part(name, value);
        });
        if (file != null) builder.part("file", file);
        return client.post().uri("/api/tracks")
                .headers(headers -> {
                    if (token != null) headers.setBearerAuth(token);
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    /** Uploads a track as alice, checks it was created, and returns the stored track from the response. */
    protected TrackDTO uploadOk(String title, String description, String fixture) {
        return upload(Map.of("title", title, "description", description), fixture)
                .expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    /** Puts a track edit (a JSON object) with the given bearer token, or anonymously when it is null. */
    protected WebTestClient.ResponseSpec edit(String id, Map<String, ?> body, String token) {
        return client.put().uri("/api/tracks/{id}", id)
                .headers(headers -> {
                    if (token != null) headers.setBearerAuth(token);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    /** Deletes a track with the given bearer token, or anonymously when it is null. */
    protected WebTestClient.ResponseSpec delete(String id, String token) {
        return client.delete().uri("/api/tracks/{id}", id)
                .headers(headers -> {
                    if (token != null) headers.setBearerAuth(token);
                })
                .exchange();
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

    /** The account a bearer token authenticates as. */
    protected AccountRef accountOf(String token) {
        return client.get().uri("/api/me").headers(headers -> headers.setBearerAuth(token)).exchange()
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();
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
