package org.tunelad.playlist;

import org.junit.jupiter.api.Test;
import org.tunelad.AbstractApiTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The OpenAPI description lists the favourite and playlist operations, with their tags and security. */
class OpenApiPlaylistTest extends AbstractApiTest {

    @Test
    void the_description_lists_the_favourite_and_playlist_operations() throws Exception {
        var body = client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode docs = JsonMapper.builder().build().readTree(body);
        var paths = docs.get("paths");

        assertThat(operations(paths, "/api/tracks/{id}/favourite")).containsExactlyInAnyOrder("put", "delete");
        assertThat(operations(paths, "/api/me/favourites")).containsExactly("get");
        assertThat(operations(paths, "/api/playlists")).containsExactlyInAnyOrder("get", "post");
        assertThat(operations(paths, "/api/playlists/{id}")).containsExactlyInAnyOrder("get", "put", "delete");
        assertThat(operations(paths, "/api/playlists/{id}/tracks")).containsExactly("put");
        assertThat(operations(paths, "/api/playlists/{id}/tracks/{trackId}")).containsExactlyInAnyOrder("put", "delete");

        assertThat(paths.get("/api/playlists").get("post").get("tags").get(0).asString()).isEqualTo("Playlists");
        assertThat(paths.get("/api/me/favourites").get("get").get("tags").get(0).asString()).isEqualTo("Favourites");
        assertThat(paths.get("/api/playlists").get("get").get("security").toString()).contains("bearerAuth");
        assertThat(paths.get("/api/playlists/{id}").get("get").get("responses").get("200").toString())
                .contains("#/components/schemas/PlaylistDTO");
        assertThat(paths.get("/api/me/favourites").get("get").get("responses").get("200").toString())
                .contains("#/components/schemas/TrackDTO");
        var schemas = docs.get("components").get("schemas");
        assertThat(schemas.propertyNames()).contains("PlaylistDTO", "PlaylistSummary", "PlaylistInput");
    }

    private static java.util.List<String> operations(JsonNode paths, String path) {
        assertThat(paths.has(path)).as("path %s", path).isTrue();
        return paths.get(path).propertyNames().stream().toList();
    }
}
