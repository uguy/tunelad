package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * What Identification proposes for the Performances of a Track. It carries no account: it is the system's, so it can
 * never overwrite a Performance an Account corrected, whoever asks.
 */
public record ProposePerformances(@NotBlank String trackId, @NotNull List<Proposal> proposals) implements TrackCommand {

    /** One Performance's proposed start, by its position in the Track (from 1). */
    public record Proposal(int position, double startOffsetSeconds) {
    }
}
