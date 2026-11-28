package org.tunelad.track.event;

import org.tunelad.track.TrackDTO;

/**
 * A Catalogue Track was put on an Album. It is not a {@link NewTrackSaved}: it has no audio to analyse or tag, so
 * only the listeners that show or find Tracks hear of it.
 */
public record CatalogueTrackAdded(TrackDTO track) {

	/** The id of the track, for listeners that only need to tell which one. */
	public String trackId() {
		return track.id();
	}
}
