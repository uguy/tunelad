package org.tunelad.track.event;

import org.tunelad.track.TrackDTO;

/** A track's metadata changed; carries the track as it now is. */
public record TrackUpdated(TrackDTO track) {

	/** The id of the track, for listeners that only need to tell which one. */
	public String trackId() {
		return track.id();
	}
}
