package org.tunelad.search.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;
import org.tunelad.track.event.NewTrackSaved;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The search module on its own: track events in, read model out. */
@ApplicationModuleTest
@Import(TestContainerDefinitions.class)
class SearchProjectionTest {

    private static final TrackOwner ALICE = new TrackOwner("account-alice", "Alice Liddell");

    @Autowired SearchTracks searchTracks;

    @BeforeEach
    void emptyReadModel() {
        searchTracks.deleteAll().block();
    }

    @Test
    void new_track_saved_adds_the_track_to_the_read_model(Scenario scenario) {
        var track = kneesUp(UUID.randomUUID().toString());

        scenario.publish(new NewTrackSaved(track))
                .andWaitForStateChange(() -> searchTracks.findById(track.id()).block())
                .andVerify(stored -> assertThat(stored).isEqualTo(
                        new SearchTrack(track.id(), "Knees Up", "A reel and a song", "Breabach",
                                "Frenzy of the Meeting", TrackFormat.MP3, ALICE)));
    }

    @Test
    void a_track_saved_again_replaces_its_copy(Scenario scenario) {
        var track = kneesUp(UUID.randomUUID().toString());
        scenario.publish(new NewTrackSaved(track))
                .andWaitForStateChange(() -> searchTracks.findById(track.id()).block())
                .andVerify(stored -> assertThat(stored).isNotNull());

        // At-least-once delivery: the same track arrives again (here with a changed title, to see it was handled)
        var again = new TrackDTO(track.id(), "Knees Up Again", track.artist(), track.album(), track.description(),
                track.format(), track.owner());
        scenario.publish(new NewTrackSaved(again))
                .andWaitForStateChange(() -> searchTracks.findById(track.id()).block(),
                        stored -> stored != null && stored.title().equals("Knees Up Again"))
                .andVerify(stored -> assertThat(searchTracks.count().block()).isEqualTo(1));
    }

    private static TrackDTO kneesUp(String id) {
        return new TrackDTO(id, "Knees Up", "Breabach", "Frenzy of the Meeting", "A reel and a song", TrackFormat.MP3,
                ALICE);
    }
}
