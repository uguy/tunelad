package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.track.command.AddCatalogueTrack;
import org.tunelad.track.command.AddCatalogueTrack.Listed;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the Albums and Catalogue Tracks of openspec/changes/albums-and-catalogue-tracks-from-thesession-org-recordings. */
class AlbumsTest extends AbstractApiTest {

    @Autowired
    TrackService tracks;

    private WebTestClient.ResponseSpec postAlbum(Map<String, ?> body, String token) {
        return client.post().uri("/api/albums").headers(h -> {
            if (token != null) h.setBearerAuth(token);
        }).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private AlbumDTO createAlbum(String title, String artist) {
        return postAlbum(Map.of("title", title, "artist", artist), UPLOADER_TOKEN)
                .expectStatus().isCreated().expectBody(AlbumDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO catalogueTrack(String albumId, String title, int position, Listed... listed) {
        return tracks.execute(new AddCatalogueTrack(albumId, title, position, List.of(listed), accountOf(UPLOADER_TOKEN)))
                .block(Duration.ofSeconds(30));
    }

    private TrackDTO getTrack(String id) {
        return client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    @Test
    void create_an_album_by_hand() {
        var created = createAlbum("At the End of the Day", "Dervish");

        var fetched = client.get().uri("/api/albums/{id}", created.id()).exchange().expectStatus().isOk()
                .expectBody(AlbumDTO.class).returnResult().getResponseBody();
        assertThat(fetched.title()).isEqualTo("At the End of the Day");
        assertThat(fetched.artist()).isEqualTo("Dervish");
        assertThat(fetched.tracks()).isEmpty();
        var listed = client.get().uri("/api/albums").exchange().expectStatus().isOk()
                .expectBodyList(AlbumDTO.class).returnResult().getResponseBody();
        assertThat(listed).extracting(AlbumDTO::id).containsExactly(created.id());
    }

    @Test
    void blank_title() {
        postAlbum(Map.of("title", "  ", "artist", "Dervish"), UPLOADER_TOKEN).expectStatus().isBadRequest();
    }

    @Test
    void creating_needs_authentication_and_reading_does_not() {
        postAlbum(Map.of("title", "A", "artist", "B"), null).expectStatus().isUnauthorized();
        client.get().uri("/api/albums").exchange().expectStatus().isOk();
        client.get().uri("/api/albums/{id}", "none").exchange().expectStatus().isNotFound();
    }

    @Test
    void fetch_a_catalogue_track() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var cooleys = createTune("Cooley's", "reel");
        var created = catalogueTrack(album.id(), "Cooley's", 1, new Listed("Cooley's", cooleys));

        var track = getTrack(created.id());

        assertThat(track.catalogue()).isTrue();
        assertThat(track.albumId()).isEqualTo(album.id());
        assertThat(track.albumPosition()).isEqualTo(1);
        assertThat(track.format()).isNull();
        assertThat(track.artist()).isEqualTo("Dervish");
        var fetched = client.get().uri("/api/albums/{id}", album.id()).exchange().expectStatus().isOk()
                .expectBody(AlbumDTO.class).returnResult().getResponseBody();
        assertThat(fetched.tracks()).extracting(TrackDTO::id).containsExactly(created.id());
    }

    @Test
    void tracks_of_an_album_come_in_order() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var second = catalogueTrack(album.id(), "Second", 2);
        var first = catalogueTrack(album.id(), "First", 1);

        var fetched = client.get().uri("/api/albums/{id}", album.id()).exchange().expectStatus().isOk()
                .expectBody(AlbumDTO.class).returnResult().getResponseBody();

        assertThat(fetched.tracks()).extracting(TrackDTO::id).containsExactly(first.id(), second.id());
    }

    @Test
    void an_ordinary_track_is_not_a_catalogue_track() {
        var track = uploadOk("Knees Up", "A reel", KNEES_UP_MP3);

        assertThat(getTrack(track.id()).catalogue()).isFalse();
    }

    @Test
    void no_audio_to_play() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var track = catalogueTrack(album.id(), "Cooley's", 1);

        client.get().uri("/api/tracks/{id}/audio", track.id()).exchange().expectStatus().isNotFound();
        play(track.id()).expectStatus().isNotFound();
    }

    @Test
    void unresolved_performance() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var cooleys = createTune("Cooley's", "reel");
        var created = catalogueTrack(album.id(), "Mixed", 1,
                new Listed("Cooley's", cooleys), new Listed("The Silver Spear", null));

        var performances = getTrack(created.id()).performances();

        assertThat(performances).hasSize(2);
        assertThat(performances.get(1).listedName()).isEqualTo("The Silver Spear");
        assertThat(performances.get(1).tuneId()).isNull();
        assertThat(performances.get(1).tuneName()).isNull();
        assertThat(client.get().uri("/api/tunes/{id}/performances", cooleys).exchange().expectStatus().isOk()
                .expectBodyList(TunePerformanceDTO.class).returnResult().getResponseBody()).hasSize(1);
    }

    @Test
    void the_albums_of_a_tune() {
        var album = createAlbum("At the End of the Day", "Dervish");
        createAlbum("Elsewhere", "Dervish");
        var cooleys = createTune("Cooley's", "reel");
        catalogueTrack(album.id(), "One", 1, new Listed("Cooley's", cooleys));
        catalogueTrack(album.id(), "Two", 2, new Listed("Cooley's", cooleys));

        var albums = client.get().uri("/api/tunes/{id}/albums", cooleys).exchange().expectStatus().isOk()
                .expectBodyList(AlbumDTO.class).returnResult().getResponseBody();

        assertThat(albums).extracting(AlbumDTO::id).containsExactly(album.id());
        assertThat(albums.get(0).artist()).isEqualTo("Dervish");
        client.get().uri("/api/tunes/{id}/albums", "none").exchange().expectStatus().isOk()
                .expectBodyList(AlbumDTO.class).hasSize(0);
    }

    @Test
    void a_set_only_when_all_performances_resolve() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var cooleys = createTune("Cooley's", "reel");
        var kesh = createTune("The Kesh", "jig");

        var resolved = catalogueTrack(album.id(), "Resolved", 1, new Listed("Cooley's", cooleys), new Listed("The Kesh", kesh));
        var partial = catalogueTrack(album.id(), "Partial", 2, new Listed("Cooley's", cooleys), new Listed("Unknown", null));

        var withSet = getTrack(resolved.id());
        assertThat(withSet.set()).isNotNull();
        assertThat(withSet.set().tunes()).extracting(s -> s.id()).containsExactly(cooleys, kesh);
        assertThat(withSet.performances()).extracting(PerformanceDTO::tuneName).containsExactly("Cooley's", "The Kesh");
        var withoutSet = getTrack(partial.id());
        assertThat(withoutSet.set()).isNull();
        assertThat(withoutSet.performances()).hasSize(2);
        assertThat(withoutSet.performances().get(0).listedName()).isEqualTo("Cooley's");
    }
}
