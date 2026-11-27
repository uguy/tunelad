package org.tunelad.favourite;

import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.track.TrackDTO;

import java.util.List;

/** What the favourite tests share: a second user, and the favourite calls. */
abstract class AbstractFavouriteTest extends AbstractApiTest {

    static final String BOB_TOKEN = TestTokens.bearer("bob", "Bob Smith");

    // ---------------------------------------------------------------------------------------------

    static WebTestClient.RequestHeadersSpec<?> withToken(WebTestClient.RequestHeadersSpec<?> request, String token) {
        return token == null ? request : request.headers(headers -> headers.setBearerAuth(token));
    }

    WebTestClient.ResponseSpec mark(String trackId, String token) {
        return withToken(client.put().uri("/api/tracks/{id}/favourite", trackId), token).exchange();
    }

    WebTestClient.ResponseSpec unmark(String trackId, String token) {
        return withToken(client.delete().uri("/api/tracks/{id}/favourite", trackId), token).exchange();
    }

    List<TrackDTO> favourites(String token) {
        return withToken(client.get().uri("/api/me/favourites"), token).exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
    }
}
