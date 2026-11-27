package org.tunelad.track;

import io.swagger.v3.oas.annotations.media.Schema;

/** A Performance of a Tune seen from the Tune: the Track it is in, with its key, notes and Transcription. */
@Schema(title = "TunePerformance", description = "One performance of a tune, with the track it is played in")
public record TunePerformanceDTO(
		String trackId,
		String trackTitle,
		@Schema(description = "The track's artist", nullable = true)
		String artist,
		@Schema(description = "Order of the tune in the track, from 1", example = "1")
		int position,
		@Schema(description = "Where the tune starts in the audio, in seconds", example = "95.5")
		double startOffsetSeconds,
		@Schema(description = "The key the tune is played in", example = "Em", nullable = true)
		String key,
		@Schema(description = "Notes on melodic variation", nullable = true)
		String notes,
		@Schema(description = "The transcription, in ABC notation", nullable = true)
		String abc,
		@Schema(description = "Whether the values were proposed from the audio (identified), edited by an account (corrected), not found by the last analysis (nothing_found), or not analysed yet (pending)", example = "identified")
		IdentificationState identification
) {
}
