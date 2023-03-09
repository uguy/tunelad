package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.tunelad.track.TrackFormat;
public record AddTrackCommand(
		@NotBlank(message = "A track needs a title.")
		@Size(min = 1, max = 80)
		String title,
		String artist,
		String album,
		String description,
		byte[] data,
		TrackFormat format) implements TrackCommand {
}
