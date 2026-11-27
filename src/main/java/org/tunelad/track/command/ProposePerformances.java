package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

/**
 * What Identification proposes for the Performances of a Track. It carries no account: it is the system's, so it can
 * never overwrite a Performance an Account corrected, whoever asks.
 *
 * @param searched the positions of the Performances whose Tune the analysis looked for: those of them nothing was
 *                 proposed for, and that were still pending, become "nothing found". The others are left as they are.
 */
public record ProposePerformances(@NotBlank String trackId, @NotNull List<Proposal> proposals,
                                  @NotNull Set<Integer> searched) implements TrackCommand {

    /** Proposals that leave the other Performances as they are. */
    public ProposePerformances(String trackId, List<Proposal> proposals) {
        this(trackId, proposals, Set.of());
    }

    /** One Performance's proposed start, by its position in the Track (from 1). */
    public record Proposal(int position, double startOffsetSeconds) {
    }
}
