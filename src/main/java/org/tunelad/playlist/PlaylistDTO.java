package org.tunelad.playlist;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.track.TrackDTO;

import java.time.Instant;
import java.util.List;

@Schema(title = "Playlist", description = "A playlist with its tracks, in playlist order")
public record PlaylistDTO(
		String id,
		String name,
		String description,
		int trackCount,
		Instant updatedAt,
		List<TrackDTO> tracks
) {
}
