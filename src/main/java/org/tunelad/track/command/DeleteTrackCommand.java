package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.tunelad.account.AccountRef;

/** Deletes a track and its audio, on behalf of {@code by}. */
public record DeleteTrackCommand(
        @NotBlank String id,
        @NotNull AccountRef by) implements TrackCommand {
}
