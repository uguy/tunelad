package org.tunelad.track.internal;

import org.tunelad.track.ArtistKind;

/** An Artist, shared by the tracks that name it. Its id is null until it is stored; its kind is null until classified. */
public record Artist(String id, String name, ArtistKind kind) {}
