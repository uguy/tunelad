package org.tunelad.playlist;

import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;

import java.util.List;
import java.util.Map;

/** What the playlist tests share: a second user, and the playlist calls. */
abstract class AbstractPlaylistTest extends AbstractApiTest {

    static final String BOB_TOKEN = TestTokens.bearer("bob", "Bob Smith");

    static WebTestClient.RequestHeadersSpec<?> withToken(WebTestClient.RequestHeadersSpec<?> request, String token) {
        return token == null ? request : request.headers(headers -> headers.setBearerAuth(token));
    }

    WebTestClient.ResponseSpec create(Map<String, ?> body, String token) {
        return withToken(client.post().uri("/api/playlists").contentType(MediaType.APPLICATION_JSON).bodyValue(body),
                token).exchange();
    }

    PlaylistSummary createOk(String name, String token) {
        return create(Map.of("name", name), token).expectStatus().isCreated()
                .expectBody(PlaylistSummary.class).returnResult().getResponseBody();
    }

    List<PlaylistSummary> list(String token) {
        return withToken(client.get().uri("/api/playlists"), token).exchange()
                .expectStatus().isOk()
                .expectBodyList(PlaylistSummary.class).returnResult().getResponseBody();
    }

    WebTestClient.ResponseSpec read(String id, String token) {
        return withToken(client.get().uri("/api/playlists/{id}", id), token).exchange();
    }

    PlaylistDTO readOk(String id, String token) {
        return read(id, token).expectStatus().isOk().expectBody(PlaylistDTO.class).returnResult().getResponseBody();
    }

    /** The titles of the playlist's tracks, in order. */
    List<String> titles(String id, String token) {
        return readOk(id, token).tracks().stream().map(t -> t.title()).toList();
    }

    WebTestClient.ResponseSpec rename(String id, Map<String, ?> body, String token) {
        return withToken(client.put().uri("/api/playlists/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body), token).exchange();
    }

    WebTestClient.ResponseSpec deletePlaylist(String id, String token) {
        return withToken(client.delete().uri("/api/playlists/{id}", id), token).exchange();
    }

    WebTestClient.ResponseSpec add(String id, String trackId, String token) {
        return withToken(client.put().uri("/api/playlists/{id}/tracks/{trackId}", id, trackId), token).exchange();
    }

    WebTestClient.ResponseSpec remove(String id, String trackId, String token) {
        return withToken(client.delete().uri("/api/playlists/{id}/tracks/{trackId}", id, trackId), token).exchange();
    }

    WebTestClient.ResponseSpec reorder(String id, List<String> trackIds, String token) {
        return withToken(client.put().uri("/api/playlists/{id}/tracks", id).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(trackIds), token).exchange();
    }
}
