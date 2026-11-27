package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.tunelad.account.AccountRef;

import java.util.List;

/** Replaces a track's metadata, on behalf of {@code by}. A null or blank description, artist or album clears it; null performances leave them as they are. */
public record UpdateTrackCommand(
        @NotBlank String id,
        @NotBlank String title,
        String artist,
        String album,
        String description,
        /** One per Performance in order; null leaves the Performances unchanged. */
        List<PerformanceInput> performances,
        @NotNull AccountRef by) implements TrackCommand {
}
