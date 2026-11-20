package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.tunelad.account.AccountRef;

/** Replaces a track's metadata, on behalf of {@code by}. A null artist or album clears it. */
public record UpdateTrackCommand(
        @NotBlank String id,
        @NotBlank String title,
        String artist,
        String album,
        @NotBlank String description,
        @NotNull AccountRef by) implements TrackCommand {
}
