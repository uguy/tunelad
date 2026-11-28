package org.tunelad.track;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.tune.DanceType;

/** One Tune as played in a Track, as the API shows it. */
@Schema(title = "Performance", description = "One tune as played in a track")
public record PerformanceDTO(
		@Schema(description = "Order in the track, from 1", example = "1")
		int position,
		@Schema(description = "Absent when the performance is unresolved: the source listed a tune that is not in the catalogue", nullable = true)
		String tuneId,
		@Schema(description = "Absent when the performance is unresolved", nullable = true)
		String tuneName,
		DanceType danceType,
		@Schema(description = "Where the tune starts in the audio, in seconds", example = "95.5")
		double startOffsetSeconds,
		@Schema(description = "The key the tune is played in", example = "Em", nullable = true)
		String key,
		@Schema(description = "Notes on melodic variation", nullable = true)
		String notes,
		@Schema(description = "The transcription, in ABC notation", nullable = true)
		String abc,
		@Schema(description = "Whether the values were proposed from the audio (identified), edited by an account (corrected), not found by the last analysis (nothing_found), or not analysed yet (pending)",
				example = "identified")
		IdentificationState identification,
		@Schema(description = "The tune name as the source listed it; present on a performance imported from a recording, "
				+ "and the only name of an unresolved performance (no tuneId, no tune)", nullable = true)
		String listedName
) {

	public PerformanceDTO(int position, String tuneId, String tuneName, DanceType danceType, double startOffsetSeconds,
						  String key, String notes, String abc, IdentificationState identification) {
		this(position, tuneId, tuneName, danceType, startOffsetSeconds, key, notes, abc, identification, null);
	}
}
