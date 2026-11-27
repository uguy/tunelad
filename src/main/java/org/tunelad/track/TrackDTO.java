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
		String description,
		TrackFormat format,
		TrackOwner owner,
		@Schema(description = "The set this track records; absent for tracks stored before sets existed", nullable = true)
		SetDTO set,
		@Schema(description = "One performance per tune of the set, in order; empty when the track has no set")
		List<PerformanceDTO> performances
) {
}

