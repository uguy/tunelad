package org.tunelad.track.event;

import org.tunelad.track.AlbumDTO;

/** An Album was created or its Tracks changed; carries the Album as it now is, without its Tracks. */
public record AlbumChanged(AlbumDTO album) {

	/** The id of the album, for listeners that only need to tell which one. */
	public String albumId() {
		return album.id();
	}
}
