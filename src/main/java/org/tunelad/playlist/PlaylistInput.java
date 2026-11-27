package org.tunelad.playlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(title = "Playlist input", description = "The name and description of a playlist")
public record PlaylistInput(

		@Schema(description = "The playlist's name", example = "Session tunes")
		@NotBlank(message = "A playlist needs a name.")
		@Size(max = 80)
		String name,

		@Schema(description = "What the playlist is for; optional")
		@Size(max = 500)
		String description
) {
}
