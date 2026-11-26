package org.tunelad.search.event;

/** A track's search copy was removed: a search no longer finds the track. */
public record TrackUnindexed(String trackId) {}
