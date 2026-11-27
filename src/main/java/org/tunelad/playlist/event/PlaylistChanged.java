package org.tunelad.playlist.event;

/** A playlist was created, renamed, deleted, or had its tracks or their order changed. */
public record PlaylistChanged(String ownerId, String playlistId) {
}
