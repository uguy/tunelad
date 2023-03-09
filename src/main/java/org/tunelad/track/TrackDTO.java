package org.tunelad.track;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(title = "Track", description = "Track details")
public record TrackDTO(

		String id,

		@NotBlank(message = "A track needs a title.")
		@Size(min = 1, max = 80)
		String title,
		String artist,
		String album,
		String description,
		TrackFormat format
) {
}

