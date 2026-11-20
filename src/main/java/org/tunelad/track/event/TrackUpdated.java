package org.tunelad.track.event;

import org.tunelad.track.TrackDTO;

/** A track's metadata changed; carries the track as it now is. */
public record TrackUpdated(TrackDTO track) {}
