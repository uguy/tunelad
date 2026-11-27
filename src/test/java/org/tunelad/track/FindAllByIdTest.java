package org.tunelad.track;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.tunelad.AbstractApiTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The read-only lookup other modules use to show tracks they only know by id. */
class FindAllByIdTest extends AbstractApiTest {

    @Autowired
    TrackService tracks;

    @Test
    void returns_the_tracks_with_those_ids_and_ignores_unknown_ids() {
        var kneesUp = uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);
        uploadOk("Mossy Banks", "A set of tunes", MOSSY_BANKS_MP3);

        var found = tracks.findAllById(List.of(kneesUp.id(), "no-such-track")).collectList()
                .block(Duration.ofSeconds(30));

        assertThat(found).containsExactly(kneesUp);
    }

    @Test
    void no_ids_means_no_tracks() {
        uploadOk("Knees Up", "A reel and a song", KNEES_UP_MP3);

        assertThat(tracks.findAllById(List.of()).collectList().block(Duration.ofSeconds(30))).isEmpty();
    }
}
