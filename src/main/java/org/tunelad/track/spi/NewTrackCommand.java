package org.tunelad.track.spi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewTrackCommand(
		@NotBlank(message = "A track needs a title.")
		@Size(min = 1, max = 80)
		String title,
		String artist,
		String album,
		String description,
		byte[] data,
		TrackFormat format) {
}
