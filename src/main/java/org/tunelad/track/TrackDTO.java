package org.tunelad.track;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.tunelad.set.SetDTO;

import java.util.List;

@Schema(title = "Track", description = "Track details")
public record TrackDTO(

		String id,

		@NotBlank(message = "A track needs a title.")
		@Size(min = 1, max = 80)
		String title,
		String artist,
		String album,
		@Schema(description = "Absent when the track has none", nullable = true)
		String description,
		TrackFormat format,
		TrackOwner owner,
		@Schema(description = "The set this track records; absent for tracks stored before sets existed", nullable = true)
		SetDTO set,
		@Schema(description = "One performance per tune of the set, in order; empty when the track has no set, except for a catalogue track whose listed tunes are not all resolved: it keeps them all")
		List<PerformanceDTO> performances,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True for a catalogue track: a place on an album with no audio yet, so it has no format and no audio stream")
		Boolean catalogue,
		@Schema(description = "The album this track is on; absent when it is on none", nullable = true)
		String albumId,
		@Schema(description = "The track's place on its album, from 1; absent when it is on no album", nullable = true)
		Integer albumPosition
) {

	/** Never null: a search result, which does not carry the flag yet, reads as an ordinary track. */
	public TrackDTO {
		catalogue = Boolean.TRUE.equals(catalogue);
	}

	public TrackDTO(String id, String title, String artist, String album, String description, TrackFormat format,
					TrackOwner owner, SetDTO set, List<PerformanceDTO> performances) {
		this(id, title, artist, album, description, format, owner, set, performances, false, null, null);
	}
}

