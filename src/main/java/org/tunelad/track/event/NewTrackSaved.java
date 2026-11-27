package org.tunelad.track.event;

import org.tunelad.track.TrackDTO;

public record NewTrackSaved(TrackDTO track) {

	/** The id of the track, for listeners that only need to tell which one. */
	public String trackId() {
		return track.id();
	}
}
