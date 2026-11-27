package org.tunelad.track.internal;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.ReactiveTransactionManager;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.MediaStore;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;
import org.tunelad.track.command.AddTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.io.IOException;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The upload's own cleanup (design.md decision 4). The audio store already rolls back the chunks of a failed upload
 * that fails or is cancelled, so the API tests in {@code FailedUploadTest} pass either way. These check that the
 * service deletes the audio itself, whatever the store does.
 */
class DefaultTrackServiceUploadTest {

    private final Tracks tracks = mock(Tracks.class);
    private final Artists artists = mock(Artists.class);
    private final MediaStore mediaStore = mock(MediaStore.class);
    private final org.tunelad.set.SetCatalog sets = mock(org.tunelad.set.SetCatalog.class);
    private final DefaultTrackService service = new DefaultTrackService(mock(TrackMapper.class), tracks, artists, mediaStore,
            sets, mock(TransactionalEvents.class), mock(ReactiveTransactionManager.class));

    private final AddTrackCommand command = new AddTrackCommand("Knees Up", null, null, "A reel and a song",
            "set-1", null, Flux.empty(), TrackFormat.FLAC, new TrackOwner("account-alice", "Alice Liddell"));

    @org.junit.jupiter.api.BeforeEach
    void noArtist() {
        when(artists.findOrCreate(any())).thenReturn(Mono.empty());
        when(sets.find("set-1")).thenReturn(Mono.just(new org.tunelad.set.SetDTO("set-1", java.util.List.of(
                new org.tunelad.set.SetDTO.Entry("tune-1", "Cooley's", org.tunelad.tune.DanceType.REEL)))));
    }

    @Test
    void audio_is_stored_with_the_format_content_type_under_a_new_id() {
        when(mediaStore.store(anyString(), any(), anyString())).thenReturn(Mono.never());

        service.execute(command).subscribe().dispose();

        verify(mediaStore).store(anyString(), any(), eq("audio/flac"));
    }

    @Test
    void failed_store_deletes_the_audio_and_saves_no_track() {
        var deleted = PublisherProbe.<Void>empty();
        when(mediaStore.store(anyString(), any(), anyString()))
                .thenReturn(Mono.error(new IOException("Simulated storage failure")));
        when(mediaStore.delete(anyString())).thenReturn(deleted.mono());

        StepVerifier.create(service.execute(command))
                .expectError(IOException.class)
                .verify(Duration.ofSeconds(5));

        deleted.assertWasSubscribed();
        verify(tracks, never()).save(any());
    }

    @Test
    void failed_cleanup_keeps_the_original_error() {
        when(mediaStore.store(anyString(), any(), anyString()))
                .thenReturn(Mono.error(new IOException("Simulated storage failure")));
        when(mediaStore.delete(anyString())).thenReturn(Mono.error(new IllegalStateException("Storage is gone")));

        StepVerifier.create(service.execute(command))
                .expectError(IOException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void cancelled_upload_deletes_the_audio() {
        when(mediaStore.store(anyString(), any(), anyString())).thenReturn(Mono.never());
        when(mediaStore.delete(anyString())).thenReturn(Mono.empty());

        service.execute(command).subscribe().dispose();

        verify(mediaStore, timeout(5000)).delete(anyString());
        verify(tracks, never()).save(any());
    }
}
