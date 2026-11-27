package org.tunelad.track.internal;

import org.tunelad.track.IdentificationState;

/** A stored Performance: one Tune of the Track's Set, with the values an Account or Identification gave it. */
public record Performance(int position, String tuneId, double startOffsetSeconds, String key, String notes, String abc,
                          IdentificationState identification) {

	/** A Performance as an Account left it: whatever it says counts as a correction once it differs from what was there. */
	public Performance with(double startOffsetSeconds, String key, String notes, String abc) {
		var same = this.startOffsetSeconds == startOffsetSeconds && java.util.Objects.equals(this.key, key)
				&& java.util.Objects.equals(this.notes, notes) && java.util.Objects.equals(this.abc, abc);
		return new Performance(position, tuneId, startOffsetSeconds, key, notes, abc,
				same ? identification : IdentificationState.CORRECTED);
	}

	/** Identification's proposal: ignored once an Account corrected the Performance. */
	public Performance proposed(double startOffsetSeconds) {
		return identification == IdentificationState.CORRECTED ? this
				: new Performance(position, tuneId, startOffsetSeconds, key, notes, abc, IdentificationState.IDENTIFIED);
	}
}
