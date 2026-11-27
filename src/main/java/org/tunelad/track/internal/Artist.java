package org.tunelad.track.internal;

/** An Artist, shared by the tracks that name it. Its id is null until it is stored. */
public record Artist(String id, String name) {}
