package org.tunelad.tune;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A Tune as the API shows it. */
@Schema(description = "A traditional tune")
public record TuneDTO(
		String id,
		String name,
		List<String> aliases,
		@Schema(nullable = true) String author,
		DanceType danceType,
		@Schema(description = "The usual key, as a hint", nullable = true) String key,
		@Schema(description = "thesession.org tune id, absent for a tune created by hand", nullable = true) Integer sourceId,
		@Schema(description = "The Reference transcription, in ABC notation", nullable = true) String abc
) {}
