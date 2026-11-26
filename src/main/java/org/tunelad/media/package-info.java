/**
 * Stored audio and playback: keeps each track's audio in PostgreSQL, under a key chosen by the caller (the track id),
 * and streams it to the browser player.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.media;
