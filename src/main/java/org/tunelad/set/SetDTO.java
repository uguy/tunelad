package org.tunelad.set;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.tune.DanceType;

import java.util.List;

/** A Set as the API shows it: its Tunes, in order. */
@Schema(description = "An ordered list of tunes")
public record SetDTO(String id, List<Entry> tunes) {

	/** A Tune of a Set. */
	@Schema(description = "A tune in a set")
	public record Entry(String id, String name, DanceType danceType) {}
}
