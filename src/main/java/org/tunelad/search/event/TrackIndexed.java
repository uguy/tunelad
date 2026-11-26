package org.tunelad.search.event;

/** A track's search copy was written, for a new track or an edited one: a search now finds the track as it is. */
public record TrackIndexed(String trackId) {}
