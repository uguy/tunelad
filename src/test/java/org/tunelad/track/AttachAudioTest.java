package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.tunelad.AbstractApiTest;
import org.tunelad.track.command.AddCatalogueTrack;
import org.tunelad.track.command.AddCatalogueTrack.Listed;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Attaching audio to a Catalogue Track and suggesting one for an upload. */
class AttachAudioTest extends AbstractApiTest {

    private static final String BOB_TOKEN = org.tunelad.TestTokens.bearer("bob", "Bob Builder");

    @Autowired
    TrackService tracks;

    private AlbumDTO createAlbum(String title, String artist) {
        return client.post().uri("/api/albums").headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("title", title, "artist", artist))
                .exchange().expectStatus().isCreated().expectBody(AlbumDTO.class).returnResult().getResponseBody();
    }

    private TrackDTO catalogueTrack(String albumId, String title, int position, Listed... listed) {
        return tracks.execute(new AddCatalogueTrack(albumId, title, position, List.of(listed), accountOf(UPLOADER_TOKEN)))
                .block(Duration.ofSeconds(30));
    }

    private TrackDTO getTrack(String id) {
        return client.get().uri("/api/tracks/{id}", id).exchange().expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
    }

    private WebTestClient.ResponseSpec attach(String id, String token) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource(KNEES_UP_MP3));
        return client.post().uri("/api/tracks/{id}/audio", id).headers(h -> {
                    if (token != null) h.setBearerAuth(token);
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build())).exchange();
    }

    @Test
    void attach_audio() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var cooleys = createTune("Cooley's", "reel");
        var created = catalogueTrack(album.id(), "Cooley's", 3, new Listed("Cooley's", cooleys));

        var attached = attach(created.id(), BOB_TOKEN).expectStatus().isOk()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();

        assertThat(attached.catalogue()).isFalse();
        var track = getTrack(created.id());
        assertThat(track.catalogue()).isFalse();
        assertThat(track.format()).isEqualTo(TrackFormat.MP3);
        assertThat(track.owner().id()).isEqualTo(accountOf(BOB_TOKEN).id());
        assertThat(track.title()).isEqualTo("Cooley's");
        assertThat(track.albumId()).isEqualTo(album.id());
        assertThat(track.albumPosition()).isEqualTo(3);
        assertThat(track.artist()).isEqualTo("Dervish");
        assertThat(track.set()).isNotNull();
        assertThat(track.performances()).extracting(PerformanceDTO::tuneId).containsExactly(cooleys);
        play(created.id()).expectStatus().is2xxSuccessful();
    }

    @Test
    void performances_of_a_track_without_a_set_survive_identification() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var created = catalogueTrack(album.id(), "Mixed", 1, new Listed("The Silver Spear", null));

        attach(created.id(), UPLOADER_TOKEN).expectStatus().isOk();

        // Tags and Identification run after the response: give them time, then check nothing was replaced
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var track = getTrack(created.id());
        assertThat(track.set()).isNull();
        assertThat(track.title()).isEqualTo("Mixed");
        assertThat(track.performances()).extracting(PerformanceDTO::listedName).containsExactly("The Silver Spear");
    }

    @Test
    void audio_already_there() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var created = catalogueTrack(album.id(), "Cooley's", 1);
        attach(created.id(), UPLOADER_TOKEN).expectStatus().isOk();

        attach(created.id(), BOB_TOKEN).expectStatus().isEqualTo(409);
        var ordinary = uploadOk("Knees Up", "A reel", KNEES_UP_MP3);
        attach(ordinary.id(), UPLOADER_TOKEN).expectStatus().isEqualTo(409);

        assertThat(getTrack(created.id()).owner().id()).isEqualTo(accountOf(UPLOADER_TOKEN).id());
        play(ordinary.id()).expectStatus().is2xxSuccessful();
    }

    @Test
    void needs_authentication_and_a_known_track() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var created = catalogueTrack(album.id(), "Cooley's", 1);

        attach(created.id(), null).expectStatus().isUnauthorized();
        attach("unknown", UPLOADER_TOKEN).expectStatus().isNotFound();
        assertThat(getTrack(created.id()).catalogue()).isTrue();
    }

    @Test
    void unsupported_format_leaves_the_catalogue_track_alone() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var created = catalogueTrack(album.id(), "Cooley's", 1);
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("mp3/Knees_Up.mp3") {
            @Override
            public String getFilename() {
                return "notes.txt";
            }
        });

        client.post().uri("/api/tracks/{id}/audio", created.id()).headers(h -> h.setBearerAuth(UPLOADER_TOKEN))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build())).exchange().expectStatus().isEqualTo(415);

        assertThat(getTrack(created.id()).catalogue()).isTrue();
    }

    @Test
    void attach_directly_at_upload() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var created = catalogueTrack(album.id(), "Cooley's", 1);
        var before = listTracks().size();
        var parts = new java.util.HashMap<String, String>();
        parts.put("setId", null);
        parts.put("catalogueTrackId", created.id());

        var result = upload(parts, KNEES_UP_MP3, BOB_TOKEN).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();

        assertThat(result.id()).isEqualTo(created.id());
        assertThat(listTracks()).hasSize(before);
        var track = getTrack(created.id());
        assertThat(track.catalogue()).isFalse();
        assertThat(track.albumId()).isEqualTo(album.id());
        assertThat(track.owner().id()).isEqualTo(accountOf(BOB_TOKEN).id());
        play(created.id()).expectStatus().is2xxSuccessful();
    }

    @Test
    void direct_attach_conflict_and_unknown() {
        var ordinary = uploadOk("Knees Up", "A reel", KNEES_UP_MP3);
        var before = listTracks().size();
        var conflict = new java.util.HashMap<String, String>();
        conflict.put("setId", null);
        conflict.put("catalogueTrackId", ordinary.id());
        var unknown = new java.util.HashMap<String, String>();
        unknown.put("setId", null);
        unknown.put("catalogueTrackId", "unknown");

        upload(conflict, KNEES_UP_MP3).expectStatus().isEqualTo(409);
        upload(unknown, KNEES_UP_MP3).expectStatus().isNotFound();

        assertThat(listTracks()).hasSize(before);
    }

    @Test
    void suggestions_match_ignoring_case_and_punctuation() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var cooleys = catalogueTrack(album.id(), "Cooley's", 1);
        var joined = catalogueTrack(album.id(), "Silver Spear, The Kesh", 2,
                new Listed("Silver Spear", null), new Listed("The Kesh", null));
        catalogueTrack(album.id(), "Other tune", 3);
        var otherAlbum = createAlbum("Another", "Altan");
        catalogueTrack(otherAlbum.id(), "Cooley's", 1);

        var uploaded = upload(Map.of("title", "  cooleys! ", "artist", "dervish"), KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
        var uploadedJoined = upload(Map.of("title", "silver spear the kesh", "artist", "Dervish"), KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();

        assertThat(suggestions(uploaded.id())).extracting(TrackDTO::id).containsExactly(cooleys.id());
        assertThat(suggestions(uploadedJoined.id())).extracting(TrackDTO::id).containsExactly(joined.id());
    }

    @Test
    void no_suggestions_without_a_match_an_artist_or_for_a_catalogue_track() {
        var album = createAlbum("At the End of the Day", "Dervish");
        var cooleys = catalogueTrack(album.id(), "Cooley's", 1);

        var other = upload(Map.of("title", "Cooley's", "artist", "Altan"), KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();
        var noArtist = upload(Map.of("title", "Cooley's"), KNEES_UP_MP3).expectStatus().isCreated()
                .expectBody(TrackDTO.class).returnResult().getResponseBody();

        assertThat(suggestions(other.id())).isEmpty();
        assertThat(suggestions(noArtist.id())).isEmpty();
        assertThat(suggestions(cooleys.id())).isEmpty();
        client.get().uri("/api/tracks/{id}/catalogue-suggestions", "unknown").exchange().expectStatus().isNotFound();
    }

    private List<TrackDTO> suggestions(String id) {
        return client.get().uri("/api/tracks/{id}/catalogue-suggestions", id).exchange().expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
    }
}
