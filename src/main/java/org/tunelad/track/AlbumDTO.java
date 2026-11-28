package org.tunelad.track;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** An Album as the API shows it; its Tracks are listed only when it is fetched by id. */
@Schema(title = "Album", description = "A release by one artist, with its tracks in order")
public record AlbumDTO(
		String id,
		String title,
		@Schema(description = "The artist's name", example = "Dervish")
		String artist,
		String artistId,
		@Schema(description = "Absent when the album has no cover", nullable = true)
		String coverUrl,
		@Schema(description = "Absent when the release date is unknown", nullable = true)
		LocalDate releaseDate,
		@Schema(description = "The album's tracks by position, catalogue tracks included; empty in a list of albums")
		List<TrackDTO> tracks
) {
}
