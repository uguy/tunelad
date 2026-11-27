package org.tunelad.track.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.tunelad.TestContainerDefinitions;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/** Tracks are saved with optimistic locking: an edit made on a stale copy is refused, not silently lost. */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES, extraIncludes = "tune")
@Import(TestContainerDefinitions.class)
class TrackVersioningTest {

    @Autowired
    Tracks tracks;

    @Autowired
    Artists artists;

    @Test
    void saving_a_stale_copy_fails() {
        var track = new Track();
        track.setTitle("Knees Up");
        var saved = tracks.save(track).block();
        assertThat(saved.getVersion()).isEqualTo(1);

        var first = tracks.findById(saved.getId()).block();
        var second = tracks.findById(saved.getId()).block();
        first.setTitle("First edit");
        second.setTitle("Second edit");

        tracks.save(first).block();
        StepVerifier.create(tracks.save(second)).verifyError(OptimisticLockingFailureException.class);
        assertThat(tracks.findById(saved.getId()).block().getTitle()).isEqualTo("First edit");
    }

    @Test
    void artist_and_owner_survive_a_round_trip() {
        var track = new Track();
        track.setTitle("Reel");
        track.setArtist(artists.findOrCreate("Alice").block());
        track.setOwner(new org.tunelad.track.TrackOwner("account-alice", "Alice Liddell"));
        track.setTags(java.util.List.of("reel", "fiddle"));

        var loaded = tracks.findById(tracks.save(track).block().getId()).block();

        assertThat(loaded.getArtist().name()).isEqualTo("Alice");
        assertThat(loaded.getOwner()).isEqualTo(new org.tunelad.track.TrackOwner("account-alice", "Alice Liddell"));
        assertThat(loaded.getTags()).containsExactly("reel", "fiddle");
    }
}
