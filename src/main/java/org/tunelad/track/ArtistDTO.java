package org.tunelad.track;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** An Artist with its kind and, by kind, its members or the bands it belongs to. */
@Schema(title = "Artist", description = "A musician, a band or a not yet classified artist")
public record ArtistDTO(
		String id,
		String name,
		@Schema(description = "Absent while the artist is not classified", nullable = true)
		ArtistKind kind,
		@Schema(description = "The musicians of a band, ordered by name; empty for anything else")
		List<Ref> members,
		@Schema(description = "The bands a musician belongs to, ordered by name; empty for anything else")
		List<Ref> bands
) {

	@Schema(title = "ArtistRef", description = "An artist named by id")
	public record Ref(String id, String name) {
	}
}
