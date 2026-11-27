package org.tunelad.track.internal;

/** A stored Performance: one Tune of the Track's Set, with the values an Account gave it. */
public record Performance(int position, String tuneId, double startOffsetSeconds, String key, String notes, String abc) {

	public Performance with(double startOffsetSeconds, String key, String notes, String abc) {
		return new Performance(position, tuneId, startOffsetSeconds, key, notes, abc);
	}
}
