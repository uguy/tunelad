package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;

/**
 * The tags read from a Track's audio. Each one fills the matching field of the Track unless an Account provided that
 * field (at upload or by an edit); null or blank values are ignored. Like {@link ProposePerformances} it carries no
 * account: it is the system's, so it can never take back what an Account chose.
 *
 * @param description the comment tag
 */
public record ApplyTags(@NotBlank String trackId, String title, String artist, String album,
                        String description) implements TrackCommand {
}
