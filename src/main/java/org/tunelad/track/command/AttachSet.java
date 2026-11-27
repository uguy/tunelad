package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Gives a Track that has no Set the Set Identification derived from its audio, with one identified Performance per
 * Tune, each starting where the Tune was heard. Like {@link ProposePerformances} it carries no account: it is the
 * system's, and it is ignored when the Track already has a Set, whoever gave it.
 *
 * @param startOffsets seconds into the audio where each Tune of the Set starts, in the Set's order
 */
public record AttachSet(@NotBlank String trackId, @NotBlank String setId,
                        @NotNull List<Double> startOffsets) implements TrackCommand {
}
