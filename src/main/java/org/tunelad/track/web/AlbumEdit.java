package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(title = "AlbumEdit", description = "The values of a new album")
public record AlbumEdit(
		@NotBlank(message = "An album needs a title.")
		@Size(max = 200)
		@Schema(example = "At the End of the Day")
		String title,
		@NotBlank(message = "An album needs an artist.")
		@Size(max = 200)
		@Schema(description = "The artist's name; found ignoring case, or created", example = "Dervish")
		String artist,
		@Schema(description = "URL of the cover image", nullable = true)
		@Size(max = 2000)
		String coverUrl,
		@Schema(description = "ISO date", example = "1996-05-01", nullable = true)
		LocalDate releaseDate
) {
}
